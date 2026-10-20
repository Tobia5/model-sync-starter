package io.modelsync.jdbc;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.port.QuotaGate;
import io.modelsync.core.QuotaAdmission;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Shared smooth pacing: every pod using the same quota row shares a single no-burst budget. */
public final class JdbcQuotaGate implements QuotaGate {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate independent;
  private final Clock clock;

  public JdbcQuotaGate(JdbcTemplate jdbc, PlatformTransactionManager manager, Clock databaseClock) {
    this.jdbc = jdbc;
    this.clock = databaseClock;
    independent = new TransactionTemplate(manager);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
    return Objects.requireNonNull(
        independent.execute(
            status -> {
              long[] q =
                  jdbc.queryForObject(
                      "select spacing_ms,next_allowed_ms from MS_QUOTA where quota_group=? for"
                          + " update",
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
            }));
  }

  @Override
  public void cooldown(String group, Instant until) {
    independent.executeWithoutResult(
        status -> {
          Long previous =
              jdbc.queryForObject(
                  "select next_allowed_ms from MS_QUOTA where quota_group=? for update",
                  Long.class,
                  group);
          if (previous == null) throw new IllegalStateException("Unknown quota: " + group);
          jdbc.update(
              "update MS_QUOTA set next_allowed_ms=? where quota_group=?",
              Math.max(previous, until.toEpochMilli()),
              group);
        });
  }
}
