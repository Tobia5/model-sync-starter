package io.modelsync.jdbc;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Oracle-first ledger; business projections remain JPA. H2 SQL is for integration tests, not Oracle
 * proof.
 */
public final class JdbcWorkStore implements WorkStore {
  private final JdbcTemplate jdbc;

  public JdbcWorkStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void verifySchema() {
    String version =
        jdbc.queryForObject(
            "select meta_value from MS_META where meta_name='schema_version'", String.class);
    if (!"1".equals(version) && !"2".equals(version))
      throw new IllegalStateException("Unsupported Model Sync schema version");
  }

  @Override
  public boolean supportsOnDemand() {
    return "2".equals(jdbc.queryForObject(
        "select meta_value from MS_META where meta_name='schema_version'", String.class));
  }

  @Override
  public void completeOnDemand(WorkLease lease, SyncState state, Instant now) {
    if (!supportsOnDemand()) throw new IllegalStateException("ON_DEMAND requires schema v2");
    completeInternal(lease, state, null, now);
  }

  private void tx() {
    if (!TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Ledger operation outside local transaction");
  }

  private JdbcWorkRow row(ResultSet r) throws SQLException {
    long until = r.getLong("lease_until_ms");
    Long leaseUntil = r.wasNull() ? null : until;
    return new JdbcWorkRow(
        new WorkKey(
            r.getString("binding_id"),
            new SubjectKey(r.getString("tenant_id"), r.getString("subject_id"))),
        r.getInt("priority"),
        r.getLong("generation"),
        r.getInt("attempts"),
        r.getInt("force_refresh") == 1,
        r.getString("work_status"),
        r.getString("lease_token"),
        leaseUntil,
        r.getLong("next_at_ms"),
        r.getString("last_error_code"));
  }

  private Optional<JdbcWorkRow> locked(String id) {
    return jdbc
        .query("select * from MS_WORK where work_id=? for update", (r, n) -> row(r), id)
        .stream()
        .findFirst();
  }

  @Override
  public String enqueue(WorkKey key, Priority priority, boolean force, Instant now) {
    tx();
    Objects.requireNonNull(priority);
    String id = key.storageId();
    Optional<JdbcWorkRow> found = locked(id);
    if (found.isEmpty()) {
      try {
        jdbc.update(
            "insert into MS_WORK"
                + " (work_id,binding_id,tenant_id,subject_id,priority,generation,attempts,force_refresh,work_status,next_at_ms,requested_at_ms)"
                + " values (?,?,?,?,?,1,0,?,'WAITING',?,?)",
            id,
            key.binding(),
            key.subject().tenant(),
            key.subject().id(),
            priority.ordinal(),
            force ? 1 : 0,
            now.toEpochMilli(),
            now.toEpochMilli());
        return id;
      } catch (DuplicateKeyException concurrentInsert) {
        found = locked(id);
        if (found.isEmpty()) throw concurrentInsert;
      }
    }
    JdbcWorkRow r = found.orElseThrow();
    // Hash collisions are fantastically unlikely, but never treat one as the same subject.
    if (!r.key().equals(key)) throw new IllegalStateException("Work identity collision");
    String status = r.status().equals("LEASED") ? "LEASED" : "WAITING";
    int attempts = r.status().equals("DEAD") ? 0 : r.attempts();
    jdbc.update(
        "update MS_WORK set"
            + " priority=?,generation=generation+1,force_refresh=?,work_status=?,next_at_ms=?,requested_at_ms=?,attempts=?"
            + " where work_id=?",
        Math.min(r.priority(), priority.ordinal()),
        force || r.force() ? 1 : 0,
        status,
        Math.min(now.toEpochMilli(), r.nextAt()),
        now.toEpochMilli(),
        attempts,
        id);
    return id;
  }

  @Override
  public Optional<String> enqueueRefresh(WorkKey key, Priority priority, Instant now) {
    tx();
    Objects.requireNonNull(priority, "priority");
    String id = key.storageId();
    Optional<JdbcWorkRow> found = locked(id);
    if (found.isEmpty()) {
      try {
        jdbc.update("insert into MS_WORK (work_id,binding_id,tenant_id,subject_id,priority,generation,attempts,force_refresh,work_status,next_at_ms,requested_at_ms) values (?,?,?,?,?,1,0,0,'WAITING',?,?)",
            id, key.binding(), key.subject().tenant(), key.subject().id(), priority.ordinal(), now.toEpochMilli(), now.toEpochMilli());
        return Optional.of(id);
      } catch (DuplicateKeyException concurrentInsert) {
        found = locked(id);
        if (found.isEmpty()) throw concurrentInsert;
      }
    }
    JdbcWorkRow row = found.orElseThrow();
    if (!row.key().equals(key)) throw new IllegalStateException("Work identity collision");
    if (row.status().equals("DEAD")) return Optional.empty();
    if (row.status().equals("LEASED")) return Optional.of(id);
    boolean idle = row.status().equals("IDLE");
    // A visit may accelerate a periodic refresh, but must never bypass an error backoff.
    long due = idle ? now.toEpochMilli() : row.lastCode() == null
        ? Math.min(row.nextAt(), now.toEpochMilli()) : row.nextAt();
    jdbc.update("update MS_WORK set work_status='WAITING',priority=?,next_at_ms=?,generation=?,requested_at_ms=? where work_id=?",
        Math.min(row.priority(), priority.ordinal()), due,
        idle ? row.generation() + 1 : row.generation(), now.toEpochMilli(), id);
    return Optional.of(id);
  }

  @Override
  public Optional<WorkLease> claim(Priority priority, Instant now, Duration duration) {
    tx();
    // Do NOT append Oracle FETCH FIRST to FOR UPDATE. Fetch one row from the locking cursor.
    String sql =
        "select * from MS_WORK where priority=? and ((work_status='WAITING' and next_at_ms<=?) or"
            + " (work_status='LEASED' and lease_until_ms<=?)) order by next_at_ms,work_id for"
            + " update skip locked";
    Optional<JdbcWorkRow> selected =
        jdbc.execute(
            (ConnectionCallback<Optional<JdbcWorkRow>>)
                connection -> {
                  try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setInt(1, priority.ordinal());
                    ps.setLong(2, now.toEpochMilli());
                    ps.setLong(3, now.toEpochMilli());
                    ps.setFetchSize(1);
                    ps.setMaxRows(1);
                    try (ResultSet rs = ps.executeQuery()) {
                      return rs.next() ? Optional.of(row(rs)) : Optional.empty();
                    }
                  }
                });
    if (selected == null || selected.isEmpty()) return Optional.empty();
    JdbcWorkRow r = selected.get();
    String token = UUID.randomUUID().toString();
    Instant until = now.plus(duration);
    jdbc.update(
        "update MS_WORK set"
            + " work_status='LEASED',lease_token=?,lease_until_ms=?,leased_generation=generation"
            + " where work_id=?",
        token,
        until.toEpochMilli(),
        r.key().storageId());
    return Optional.of(
        new WorkLease(r.key(), token, r.generation(), r.attempts(), r.force(), priority, until));
  }

  @Override
  public boolean lockValid(WorkLease lease, Instant now) {
    tx();
    return locked(lease.key().storageId())
        .filter(
            r ->
                r.status().equals("LEASED")
                    && Objects.equals(r.token(), lease.token())
                    && r.until() != null
                    && r.until() > now.toEpochMilli())
        .isPresent();
  }

  @Override
  public SyncState state(WorkKey key) {
    tx();
    return jdbc
        .query(
            "select * from MS_STATE where work_id=?",
            (r, n) ->
                new SyncState(
                    key,
                    instant(r, "validated_at_ms"),
                    instant(r, "reconciled_at_ms"),
                    r.getString("matched_digest"),
                    r.getString("owner_validator"),
                    r.getString("outcome"),
                    instant(r, "source_changed_at_ms")),
            key.storageId())
        .stream()
        .findFirst()
        .orElseGet(() -> SyncState.empty(key));
  }

  private static Instant instant(ResultSet rs, String name) throws SQLException {
    long n = rs.getLong(name);
    return rs.wasNull() ? null : Instant.ofEpochMilli(n);
  }

  private static Long millis(Instant value) {
    return value == null ? null : value.toEpochMilli();
  }

  @Override
  public void complete(WorkLease lease, SyncState s, Instant nextAt, Instant now) {
    completeInternal(lease, s, Objects.requireNonNull(nextAt, "nextAt"), now);
  }

  private void completeInternal(WorkLease lease, SyncState s, Instant nextAt, Instant now) {
    tx();
    JdbcWorkRow r = currentToken(lease);
    int updated =
        jdbc.update(
            "update MS_STATE set"
                + " validated_at_ms=?,reconciled_at_ms=?,matched_digest=?,owner_validator=?,outcome=?,source_changed_at_ms=?"
                + " where work_id=?",
            millis(s.validatedAt()),
            millis(s.reconciledAt()),
            s.matchedDigest(),
            s.validator(),
            s.outcome(),
            millis(s.sourceChangedAt()),
            lease.key().storageId());
    if (updated == 0)
      jdbc.update(
          "insert into MS_STATE"
              + " (work_id,validated_at_ms,reconciled_at_ms,matched_digest,owner_validator,outcome,source_changed_at_ms)"
              + " values (?,?,?,?,?,?,?)",
          lease.key().storageId(),
          millis(s.validatedAt()),
          millis(s.reconciledAt()),
          s.matchedDigest(),
          s.validator(),
          s.outcome(),
          millis(s.sourceChangedAt()));
    boolean pending = r.generation() != lease.generation();
    boolean idle = !pending && nextAt == null;
    jdbc.update(
        "update MS_WORK set"
            + " work_status=?,priority=?,force_refresh=?,next_at_ms=?,attempts=0,last_error_code=null,lease_token=null,lease_until_ms=null"
            + " where work_id=?",
        idle ? "IDLE" : "WAITING",
        pending ? r.priority() : Priority.BACKGROUND.ordinal(),
        pending && r.force() ? 1 : 0,
        (pending || idle ? now : nextAt).toEpochMilli(),
        lease.key().storageId());
  }

  @Override
  public void reschedule(
      WorkLease lease, Instant nextAt, String code, boolean failure, int maxAttempts, Instant now) {
    tx();
    JdbcWorkRow r = currentToken(lease);
    boolean pending = r.generation() != lease.generation();
    int attempts = pending ? 0 : r.attempts() + (failure ? 1 : 0);
    String status = attempts >= maxAttempts ? "DEAD" : "WAITING";
    jdbc.update(
        "update MS_WORK set"
            + " work_status=?,next_at_ms=?,attempts=?,last_error_code=?,lease_token=null,lease_until_ms=null"
            + " where work_id=?",
        status,
        nextAt.toEpochMilli(),
        attempts,
        code,
        lease.key().storageId());
  }

  private JdbcWorkRow currentToken(WorkLease lease) {
    JdbcWorkRow r = locked(lease.key().storageId()).orElseThrow(LeaseLost::new);
    if (!r.status().equals("LEASED") || !Objects.equals(r.token(), lease.token()))
      throw new LeaseLost();
    return r;
  }
}
