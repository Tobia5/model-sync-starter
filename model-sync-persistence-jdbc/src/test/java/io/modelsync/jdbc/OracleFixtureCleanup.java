package io.modelsync.jdbc;

import io.modelsync.core.domain.SubjectKey;
import io.modelsync.core.domain.WorkKey;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Teardown only: never used by a shipped adapter or to prepare measured work. */
final class OracleFixtureCleanup {
  private OracleFixtureCleanup() {}

  static boolean ownedContainer(Map<String, String> environment, String url, String user) {
    String mode = environment.get("MODELSYNC_ORACLE_CONTAINER");
    if (mode == null || mode.isEmpty()) return false;
    if (!"EXPLICIT_DISPOSABLE".equals(mode)
        || !"DISPOSABLE_NONPRODUCTION".equals(environment.get("MODELSYNC_FULL_ENV_ACK"))
        || !"DEDICATED_TEST_SCHEMA".equals(environment.get("MODELSYNC_ORACLE_TEST_SCHEMA_ACK"))
        || !"MS_TEST_RUNTIME".equals(user)
        || url == null || !url.matches("jdbc:oracle:thin:@127\\.0\\.0\\.1:[1-9][0-9]{0,4}/FREEPDB1")
        || Integer.parseInt(url.substring(url.lastIndexOf(':') + 1, url.lastIndexOf('/'))) > 65535) {
      throw new IllegalStateException("Disposable teardown requires the generated loopback runtime schema and acknowledgments");
    }
    return true;
  }

  static Map<String, Object> preflight(JdbcTemplate jdbc, boolean owned) {
    if (jdbc.queryForObject("select count(*) from MS_WORK", Long.class) != 0
        || jdbc.queryForObject("select count(*) from MS_STATE", Long.class) != 0)
      throw new IllegalStateException("Cleanup preflight requires an empty dedicated fixture");
    String binding = "cleanup-proof-" + UUID.randomUUID();
    WorkKey key = new WorkKey(binding, new SubjectKey("synthetic", "proof"));
    try {
      jdbc.update("insert into MS_WORK (work_id,binding_id,tenant_id,subject_id,priority,generation,attempts,force_refresh,work_status,next_at_ms,requested_at_ms) values (?,?,?,?,2,1,0,0,'WAITING',0,0)",
          key.storageId(), binding, key.subject().tenant(), key.subject().id());
      jdbc.update("insert into MS_STATE (work_id,outcome) values (?,'MATCH')", key.storageId());
      return clearLedger(jdbc, binding, owned);
    } finally {
      jdbc.update("delete from MS_STATE where work_id=?", key.storageId());
      jdbc.update("delete from MS_WORK where work_id=?", key.storageId());
    }
  }

  static Map<String, Object> clearLedger(JdbcTemplate jdbc, String binding, boolean owned) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Fixture DDL must run after worker transactions have drained");
    if (!owned) {
      jdbc.update("delete from MS_STATE where work_id in (select work_id from MS_WORK where binding_id=?)", binding);
      jdbc.update("delete from MS_WORK where binding_id=?", binding);
      return Map.of("strategy", "SCOPED_DELETE", "scope_verified", true);
    }
    if (jdbc.queryForObject("select count(*) from MS_WORK where binding_id<>?", Long.class, binding) != 0
        || jdbc.queryForObject("select count(*) from MS_STATE s where not exists (select 1 from MS_WORK w where w.work_id=s.work_id and w.binding_id=?)", Long.class, binding) != 0)
      throw new IllegalStateException("Refusing disposable teardown with unrelated fixture rows");
    String foreignKey = stateForeignKey(jdbc.queryForList("select c.table_name,c.constraint_name,c.status,c.validated from user_constraints c join user_constraints p on p.constraint_name=c.r_constraint_name and c.r_owner=user where c.constraint_type='R' and p.table_name='MS_WORK'"));
    // Oracle refuses parent TRUNCATE even with an empty enabled child. Restore in every path.
    jdbc.execute("truncate table MS_STATE");
    truncateWithRestoration(jdbc, foreignKey);
    String restored = stateForeignKey(jdbc.queryForList("select c.table_name,c.constraint_name,c.status,c.validated from user_constraints c join user_constraints p on p.constraint_name=c.r_constraint_name and c.r_owner=user where c.constraint_type='R' and p.table_name='MS_WORK'"));
    if (!foreignKey.equals(restored)
        || jdbc.queryForObject("select count(*) from MS_WORK", Long.class) != 0
        || jdbc.queryForObject("select count(*) from MS_STATE", Long.class) != 0)
      throw new IllegalStateException("Disposable teardown or constraint restoration unverified");
    String orphan = new WorkKey(binding, new SubjectKey("synthetic", "orphan-proof")).storageId();
    try {
      jdbc.update("insert into MS_STATE (work_id,outcome) values (?,'MATCH')", orphan);
      jdbc.update("delete from MS_STATE where work_id=?", orphan);
      throw new IllegalStateException("Restored foreign key accepted an orphan");
    } catch (DataIntegrityViolationException expected) {
      if (!missingParent(expected))
        throw new IllegalStateException("Foreign-key enforcement probe failed for another reason", expected);
    }
    return Map.of("strategy", "OWNED_CONTAINER_TRUNCATE", "scope_verified", true,
        "foreign_key_restored", true, "orphan_probe_rejected", true);
  }

  static boolean missingParent(Throwable failure) {
    // ojdbc11's deepest OracleDatabaseException is not a SQLException. Inspect the SQL wrapper too.
    for (int depth = 0; failure != null && depth < 16; depth++, failure = failure.getCause())
      if (failure instanceof SQLException sql && sql.getErrorCode() == 2291) return true;
    return false;
  }

  static String stateForeignKey(List<Map<String, Object>> constraints) {
    if (constraints.size() != 1 || !"MS_STATE".equals(constraints.getFirst().get("TABLE_NAME"))
        || !"ENABLED".equals(constraints.getFirst().get("STATUS"))
        || !"VALIDATED".equals(constraints.getFirst().get("VALIDATED")))
      throw new IllegalStateException("Expected exactly the enabled validated MS_STATE foreign key");
    String name = String.valueOf(constraints.getFirst().get("CONSTRAINT_NAME"));
    identifier(name);
    return name;
  }

  static void truncateWithRestoration(JdbcTemplate jdbc, String constraint) {
    String quoted = identifier(constraint);
    jdbc.execute("alter table MS_STATE disable constraint " + quoted);
    Throwable primary = null;
    try {
      jdbc.execute("truncate table MS_WORK");
    } catch (RuntimeException | Error failure) {
      primary = failure;
      throw failure;
    } finally {
      try {
        jdbc.execute("alter table MS_STATE enable validate constraint " + quoted);
      } catch (RuntimeException | Error restoration) {
        if (primary != null) primary.addSuppressed(restoration);
        else throw restoration;
      }
    }
  }

  private static String identifier(String name) {
    if (name == null || !name.matches("[A-Z][A-Z0-9_$#]{0,127}"))
      throw new IllegalStateException("Unexpected fixture constraint identifier");
    return '"' + name + '"';
  }
}
