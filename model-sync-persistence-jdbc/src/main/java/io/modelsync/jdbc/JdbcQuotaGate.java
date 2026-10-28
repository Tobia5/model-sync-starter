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
  private final JdbcInsertPolicy insertPolicy;
  private final JdbcCooldownPolicy cooldownPolicy;
  private final JdbcCooldownPublications publications;
  private final Map<String,Instant> unrecorded = new java.util.concurrent.ConcurrentHashMap<>();

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
    this(jdbc, manager, databaseClock, lockPolicy, JdbcInsertPolicy.DEFAULT);
  }

  /** Explicit work/quota creation capability; existing-row lock behavior stays independent. */
  public JdbcQuotaGate(JdbcTemplate jdbc, PlatformTransactionManager manager, Clock databaseClock,
      JdbcQuotaLockPolicy lockPolicy, JdbcInsertPolicy insertPolicy) {
    this(jdbc,manager,databaseClock,lockPolicy,insertPolicy,JdbcCooldownPolicy.DIRECT);
  }

  public JdbcQuotaGate(JdbcTemplate jdbc, PlatformTransactionManager manager, Clock databaseClock,
      JdbcQuotaLockPolicy lockPolicy, JdbcInsertPolicy insertPolicy, JdbcCooldownPolicy cooldownPolicy) {
    this(jdbc,manager,databaseClock,lockPolicy,insertPolicy,cooldownPolicy,
        new CooldownRetry(()->java.util.concurrent.ThreadLocalRandom.current().nextDouble(),CooldownRetry::sleep));
  }

  JdbcQuotaGate(JdbcTemplate jdbc, PlatformTransactionManager manager, Clock databaseClock,
      JdbcQuotaLockPolicy lockPolicy, JdbcInsertPolicy insertPolicy, JdbcCooldownPolicy cooldownPolicy,
      CooldownRetry retry) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.clock = Objects.requireNonNull(databaseClock);
    this.lockPolicy = Objects.requireNonNull(lockPolicy);
    if (lockPolicy == JdbcQuotaLockPolicy.ORACLE_NOWAIT
        && !Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) c ->
            "Oracle".equals(c.getMetaData().getDatabaseProductName()))))
      throw new IllegalArgumentException("ORACLE_NOWAIT quota locks require an Oracle datasource");
    this.insertPolicy = Objects.requireNonNull(insertPolicy);
    insertPolicy.validate(jdbc);
    independent = new TransactionTemplate(manager);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    independent.setName("modelSyncQuota");
    this.cooldownPolicy=Objects.requireNonNull(cooldownPolicy);
    publications=cooldownPolicy==JdbcCooldownPolicy.DURABLE
        ? new JdbcCooldownPublications(jdbc,manager,clock,lockPolicy,insertPolicy,retry) : null;
  }

  public void register(String group, Duration spacing) {
    if (group == null || group.isBlank() || group.length() > 128 || spacing.toMillis() < 1)
      throw new IllegalArgumentException("Invalid quota");
    try { independent.executeWithoutResult(
        status -> {
          List<Long> existing =
              jdbc.query(
                  "select spacing_ms from MS_QUOTA where quota_group=?",
                  (r, n) -> r.getLong(1),
                  group);
          if (existing.isEmpty()) {
            try {
              jdbc.update(
                  "insert into MS_QUOTA (quota_group,spacing_ms,next_allowed_ms) values (?,?,0)" + insertPolicy.suffix(),
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
        }); }
    catch (DataAccessException failure) {
      SQLException refusal = JdbcWorkStore.oracleLockRefusal(failure);
      if (insertPolicy == JdbcInsertPolicy.ORACLE_26_NOWAIT && refusal != null)
        throw new LocalContention("quota registration",
            new SQLException("Oracle insert-lock refusal", refusal.getSQLState(), refusal.getErrorCode()));
      throw failure;
    }
  }

  @Override
  public QuotaAdmission acquire(String group) {
    Instant blocked=localBlock(group);
    if(blocked!=null)throw new CooldownUnavailable(blocked);
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
              long floor=publications==null?q[1]:Math.max(q[1],publications.floor(group));
              long now = clock.millis();
              if (now < floor) {
                if(publications!=null) {
                  jdbc.update("update MS_QUOTA set next_allowed_ms=? where quota_group=?",floor,group);
                  publications.recoverOne(group,floor);
                }
                return QuotaAdmission.waitUntil(Instant.ofEpochMilli(floor));
              }
              jdbc.update(
                  "update MS_QUOTA set next_allowed_ms=? where quota_group=?",
                  Math.addExact(now, q[0]),
                  group);
              if(publications!=null)publications.recoverOne(group,Math.max(floor,Math.addExact(now,q[0])));
              return QuotaAdmission.allow();
            })));
  }

  @Override
  public Optional<Instant> nextAdmission(String group) {
    // Plain consistent read: no FOR UPDATE, independent transaction or permit consumption.
    Long next = jdbc.queryForObject(
        "select next_allowed_ms from MS_QUOTA where quota_group=?", Long.class, group);
    if (next == null) throw new IllegalStateException("Unknown quota: " + group);
    if(publications!=null)next=Math.max(next,publications.floor(group));
    Instant blocked=localBlock(group);
    if(blocked!=null)next=Math.max(next,blocked.toEpochMilli());
    return Optional.of(Instant.ofEpochMilli(next));
  }

  public JdbcCooldownPolicy cooldownPolicy() { return cooldownPolicy; }
  @Override public boolean supportsDurableCooldowns() { return publications!=null; }

  @Override public CooldownReceipt publishCooldown(CooldownRequest request) {
    if(publications==null)return QuotaGate.super.publishCooldown(request);
    JdbcCooldownPublications.requireOutsideTransaction();
    CooldownReceipt accepted;
    try {accepted=publications.accept(request);}
    catch(RuntimeException | Error failure) {
      // Local protection only. Other nodes cannot be promised knowledge of an unrecorded response.
      unrecorded.merge(request.group(),request.until(),(a,b)->a.isAfter(b)?a:b);
      throw failure;
    }
    return publications.materialize(accepted);
  }

  /** Bounded recovery without owner I/O or admission consumption; returns observed applied receipts. */
  public int recoverCooldowns(String group,int limit) {
    if(publications==null)throw new IllegalStateException("DURABLE cooldown policy required");
    if(limit<1 || limit>64)throw new IllegalArgumentException("Cooldown recovery limit must be 1..64");
    JdbcCooldownPublications.requireOutsideTransaction();
    int done=0;
    while(done<limit) {
      var next=publications.oldest(group);if(next.isEmpty())break;
      if(publications.materialize(new CooldownReceipt(next.orElseThrow(),CooldownState.DURABLE_PENDING,true)).state()!=CooldownState.APPLIED)break;
      done++;
    }
    return done;
  }

  private Instant localBlock(String group) {
    Instant block=unrecorded.get(group);
    if(block!=null && !clock.instant().isBefore(block)){unrecorded.remove(group,block);return null;}
    return block;
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
