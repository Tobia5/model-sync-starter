package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class OracleFixtureCleanupTest {
  @Test void truncateRequiresEveryContainerAcknowledgmentAndExactLoopbackSchema() {
    String url = "jdbc:oracle:thin:@127.0.0.1:15210/FREEPDB1";
    Map<String, String> env = Map.of("MODELSYNC_ORACLE_CONTAINER", "EXPLICIT_DISPOSABLE",
        "MODELSYNC_FULL_ENV_ACK", "DISPOSABLE_NONPRODUCTION",
        "MODELSYNC_ORACLE_TEST_SCHEMA_ACK", "DEDICATED_TEST_SCHEMA");
    assertFalse(OracleFixtureCleanup.ownedContainer(Map.of(), url, "MS_TEST_RUNTIME"));
    assertTrue(OracleFixtureCleanup.ownedContainer(env, url, "MS_TEST_RUNTIME"));
    for (String key : env.keySet()) {
      var incomplete = new HashMap<>(env);
      incomplete.put(key, "wrong");
      assertThrows(IllegalStateException.class, () -> OracleFixtureCleanup.ownedContainer(incomplete, url, "MS_TEST_RUNTIME"));
    }
    assertThrows(IllegalStateException.class, () -> OracleFixtureCleanup.ownedContainer(env, url, "MS_TEST_OTHER"));
    assertThrows(IllegalStateException.class, () -> OracleFixtureCleanup.ownedContainer(env, url.replace("127.0.0.1", "remote.example"), "MS_TEST_RUNTIME"));
    assertThrows(IllegalStateException.class, () -> OracleFixtureCleanup.ownedContainer(env, url.replace("15210", "99999"), "MS_TEST_RUNTIME"));
  }

  @Test void unrelatedRowsArePreservedBeforeAnyDdl() {
    JdbcTemplate jdbc = fixture();
    jdbc.update("insert into MS_WORK values ('owned','mine'),('unrelated','other')");
    assertThrows(IllegalStateException.class, () -> OracleFixtureCleanup.clearLedger(jdbc, "mine", true));
    assertEquals(2, jdbc.queryForObject("select count(*) from MS_WORK", Integer.class));
  }

  @Test void externalCleanupDeletesOnlyItsBinding() {
    JdbcTemplate jdbc = fixture();
    jdbc.update("insert into MS_WORK values ('owned','mine'),('unrelated','other')");
    jdbc.update("insert into MS_STATE values ('owned'),('unrelated')");
    assertEquals("SCOPED_DELETE", OracleFixtureCleanup.clearLedger(jdbc, "mine", false).get("strategy"));
    assertEquals(List.of("unrelated"), jdbc.queryForList("select work_id from MS_WORK", String.class));
    assertEquals(List.of("unrelated"), jdbc.queryForList("select work_id from MS_STATE", String.class));
  }

  @Test void orphanedStateIsPreservedBeforeAnyDdl() {
    JdbcTemplate jdbc = fixture();
    jdbc.execute("drop table MS_STATE");
    jdbc.execute("create table MS_STATE (work_id varchar(64) primary key)");
    jdbc.update("insert into MS_WORK values ('owned','mine')");
    jdbc.update("insert into MS_STATE values ('orphan')");
    assertThrows(IllegalStateException.class, () -> OracleFixtureCleanup.clearLedger(jdbc, "mine", true));
    assertEquals(List.of("orphan"), jdbc.queryForList("select work_id from MS_STATE", String.class));
    assertEquals(1, jdbc.queryForObject("select count(*) from MS_WORK", Integer.class));
  }

  @Test void ambientTransactionCannotBeImplicitlyCommittedByFixtureDdl() {
    JdbcTemplate jdbc = fixture();
    var tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    tx.executeWithoutResult(status -> assertThrows(IllegalStateException.class,
        () -> OracleFixtureCleanup.clearLedger(jdbc, "mine", true)));
  }

  @Test void foreignKeyRestorationRunsAfterTruncateFailure() {
    var jdbc = new CleanupRecordingJdbcTemplate();
    jdbc.failTruncate = true;
    assertSame(jdbc.truncateFailure, assertThrows(IllegalStateException.class,
        () -> OracleFixtureCleanup.truncateWithRestoration(jdbc, "SYS_C123")));
    assertEquals("alter table MS_STATE enable validate constraint \"SYS_C123\"", jdbc.statements.getLast());
  }

  @Test void originalFailureSurvivesAnAdditionalRestorationFailure() {
    var jdbc = new CleanupRecordingJdbcTemplate();
    jdbc.failTruncate = true;
    jdbc.failRestoration = true;
    var failure = assertThrows(IllegalStateException.class,
        () -> OracleFixtureCleanup.truncateWithRestoration(jdbc, "SYS_C123"));
    assertSame(jdbc.truncateFailure, failure);
    assertArrayEquals(new Throwable[] {jdbc.restorationFailure}, failure.getSuppressed());
  }

  @Test void restorationFailureCannotProduceSuccessfulCleanup() {
    var jdbc = new CleanupRecordingJdbcTemplate();
    jdbc.failRestoration = true;
    assertSame(jdbc.restorationFailure, assertThrows(IllegalStateException.class,
        () -> OracleFixtureCleanup.truncateWithRestoration(jdbc, "SYS_C123")));
  }

  @Test void unknownForeignKeysAndUnsafeIdentifiersCannotReachDdl() {
    Map<String, Object> fk = Map.of("TABLE_NAME", "MS_STATE", "CONSTRAINT_NAME", "SYS_C123", "STATUS", "ENABLED", "VALIDATED", "VALIDATED");
    assertEquals("SYS_C123", OracleFixtureCleanup.stateForeignKey(List.of(fk)));
    assertThrows(IllegalStateException.class, () -> OracleFixtureCleanup.stateForeignKey(List.of()));
    assertThrows(IllegalStateException.class, () -> OracleFixtureCleanup.stateForeignKey(List.of(fk, fk)));
    for (String key : List.of("TABLE_NAME", "STATUS", "VALIDATED")) {
      var wrong = new HashMap<>(fk);
      wrong.put(key, "unexpected");
      assertThrows(IllegalStateException.class, () -> OracleFixtureCleanup.stateForeignKey(List.of(wrong)));
    }
    var jdbc = new CleanupRecordingJdbcTemplate();
    assertThrows(IllegalStateException.class, () -> OracleFixtureCleanup.truncateWithRestoration(jdbc, "unsafe;drop table MS_WORK"));
    assertTrue(jdbc.statements.isEmpty());
  }

  @Test void enforcementProbeUsesTheSqlWrapperRatherThanTheDeepestDiagnosticCause() {
    var sql = new SQLException("synthetic missing parent", "23000", 2291);
    sql.initCause(new Exception("driver diagnostic cause is not a SQLException"));
    assertTrue(OracleFixtureCleanup.missingParent(new DataIntegrityViolationException("probe", sql)));
    assertFalse(OracleFixtureCleanup.missingParent(new SQLException("different integrity error", "23000", 1)));
    assertFalse(OracleFixtureCleanup.missingParent(new Exception("not SQL")));
  }

  private JdbcTemplate fixture() {
    var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
    jdbc.execute("create table MS_WORK (work_id varchar(64) primary key,binding_id varchar(128) not null)");
    jdbc.execute("create table MS_STATE (work_id varchar(64) primary key references MS_WORK(work_id))");
    return jdbc;
  }
}
