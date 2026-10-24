package io.modelsync.jdbc;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.port.QuotaGate;
import io.modelsync.core.QuotaAdmission;
import java.time.*;
import java.util.*;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Shared smooth pacing: every pod using the same quota row shares a single no-burst budget. */
public final class JdbcQuotaGate implements QuotaGate {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate independent;
  private final Clock clock;
  private final JdbcQuotaLockPolicy lockPolicy;

  public JdbcQuotaGate(JdbcTemplate jdbc, PlatformTransactionManager manager, Clock databaseClock) {
    this(jdbc, manager, databaseClock, JdbcQuotaLockPolicy.DEFAULT);
  }

  /**
   * @param lockPolicy explicit existing-row Oracle NOWAIT policy; does not bound first inserts
   * @param jdbc JDBC access enlisted with manager
   * @param manager transaction manager for independent, committed admission/cooldown transactions
   * @param databaseClock shared database time
   */
  public JdbcQuotaGate(JdbcTemplate jdbc, PlatformTransactionManager manager, Clock databaseClock,
      JdbcQuotaLockPolicy lockPolicy) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.clock = Objects.requireNonNull(databaseClock);
    this.lockPolicy = Objects.requireNonNull(lockPolicy);
    if (lockPolicy == JdbcQuotaLockPolicy.ORACLE_NOWAIT
        && !Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) c ->
            "Oracle".equals(c.getMetaData().getDatabaseProductName()))))
      throw new IllegalArgumentException("ORACLE_NOWAIT quota locks require an Oracle datasource");
    independent = new TransactionTemplate(manager);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    independent.setName("modelSyncQuota");
  }

  public void register(String group, Duration spacing) {
    if (group == null || group.isBlank() || group.length() > 128 || spacing.toMillis() < 1)
      throw new IllegalArgumentException("Invalid quota");
    independent.executeWithoutResult(
        status -> {
          List<Long> existing =
              jdbc.query(
                  "select spacing_ms from MS_QUOTA where quota_group=?",
                  (r, n) -> r.getLong(1),
                  group);
          if (existing.isEmpty()) {
            try {
              jdbc.update(
                  "insert into MS_QUOTA (quota_group,spacing_ms,next_allowed_ms) values (?,?,0)",
                  group,
                  spacing.toMillis());
            } catch (DuplicateKeyException race) {
              /* re-read below; Oracle statement failure does not abort the transaction */
            }
          }
          long actual =
              Objects.requireNonNull(
                  jdbc.queryForObject(
                      "select spacing_ms from MS_QUOTA where quota_group=?", Long.class, group));
          if (actual != spacing.toMillis())
            throw new IllegalStateException(
                "Quota configuration differs from persisted configuration: " + group);
        });
  }

  @Override
  public QuotaAdmission acquire(String group) {
    return lockOperation("quota admission", () -> Objects.requireNonNull(
        independent.execute(
            status -> {
              long[] q =
                  jdbc.queryForObject(
                      "select spacing_ms,next_allowed_ms from MS_QUOTA where quota_group=? for"
                          + " update" + lockPolicy.suffix(),
                      (r, n) -> new long[] {r.getLong(1), r.getLong(2)},
                      group);
              if (q == null) throw new IllegalStateException("Unknown quota: " + group);
              long now = clock.millis();
              if (now < q[1]) return QuotaAdmission.waitUntil(Instant.ofEpochMilli(q[1]));
              jdbc.update(
                  "update MS_QUOTA set next_allowed_ms=? where quota_group=?",
                  Math.addExact(now, q[0]),
                  group);
              return QuotaAdmission.allow();
            })));
  }

  @Override
  public Optional<Instant> nextAdmission(String group) {
    // Plain consistent read: no FOR UPDATE, independent transaction or permit consumption.
    Long next = jdbc.queryForObject(
        "select next_allowed_ms from MS_QUOTA where quota_group=?", Long.class, group);
    if (next == null) throw new IllegalStateException("Unknown quota: " + group);
    return Optional.of(Instant.ofEpochMilli(next));
  }

  @Override
  public void cooldown(String group, Instant until) {
    lockOperation("quota cooldown", () -> { independent.executeWithoutResult(
        status -> {
          Long previous =
              jdbc.queryForObject(
                  "select next_allowed_ms from MS_QUOTA where quota_group=? for update" + lockPolicy.suffix(),
                  Long.class,
                  group);
          if (previous == null) throw new IllegalStateException("Unknown quota: " + group);
          jdbc.update(
              "update MS_QUOTA set next_allowed_ms=? where quota_group=?",
              Math.max(previous, until.toEpochMilli()),
              group);
        }); return null; });
  }

  private <T> T lockOperation(String operation, Supplier<T> action) {
    // Catch outside TransactionTemplate: rollback/connection return precedes propagation.
    // A successful callback is not admission until the independent commit has returned.
    try { return action.get(); }
    catch (DataAccessException failure) {
      SQLException refusal = JdbcWorkStore.oracleLockRefusal(failure);
      if (lockPolicy == JdbcQuotaLockPolicy.ORACLE_NOWAIT && refusal != null)
        throw new LocalContention(operation,
            new SQLException("Oracle row-lock refusal", refusal.getSQLState(), refusal.getErrorCode()));
      throw failure;
    }
  }
}
