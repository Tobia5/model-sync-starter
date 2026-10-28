package io.modelsync.jdbc;

import io.modelsync.core.domain.Projection;
import io.modelsync.core.domain.SubjectKey;
import io.modelsync.core.port.ProjectionStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test-only Oracle projection with a detached value and an optimistic revision. */
final class OracleTestProjectionStore implements ProjectionStore<String> {
  private final JdbcTemplate jdbc;

  OracleTestProjectionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override public Optional<Projection<String>> load(SubjectKey key) {
    return jdbc.query("select projection_value,revision,expires_at_ms from MS_TEST_PROJECTION where tenant_id=? and subject_id=?",
        (r, n) -> {
          long expiry = r.getLong(3);
          return new Projection<>(r.getString(1), r.getString(2),
              r.wasNull() ? null : Instant.ofEpochMilli(expiry));
        }, key.tenant(), key.id()).stream().findFirst();
  }

  @Override public boolean compareAndSet(SubjectKey key, String expectedRevision,
      String value, Instant expiresAt) {
    String revision = UUID.randomUUID().toString();
    Long expiry = expiresAt == null ? null : expiresAt.toEpochMilli();
    if (expectedRevision != null) {
      return jdbc.update("update MS_TEST_PROJECTION set projection_value=?,revision=?,expires_at_ms=? where tenant_id=? and subject_id=? and revision=?",
          value, revision, expiry, key.tenant(), key.id(), expectedRevision) == 1;
    }
    try {
      return jdbc.update("insert into MS_TEST_PROJECTION (tenant_id,subject_id,projection_value,revision,expires_at_ms) values (?,?,?,?,?)",
          key.tenant(), key.id(), value, revision, expiry) == 1;
    } catch (DuplicateKeyException concurrentInsert) {
      return false;
    }
  }
}
