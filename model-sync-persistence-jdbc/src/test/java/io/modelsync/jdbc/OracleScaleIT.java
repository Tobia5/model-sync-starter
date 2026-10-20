package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.Priority;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Measures a ledger claim with a large due ledger in a dedicated disposable schema. */
class OracleScaleIT {
  private static final int ROWS = 100_000;
  private static final String SESSION_WORK = "select sum(s.value) from v$mystat s join v$statname n"
      + " on n.statistic#=s.statistic# where n.name in ('db block gets','consistent gets','physical reads')";

  @Test void dueLedgerAdmissionHasMeasuredDatabaseWork() throws Exception {
    assertEquals("DEDICATED_TEST_SCHEMA", System.getenv("MODELSYNC_ORACLE_TEST_SCHEMA_ACK"));
    String url = System.getenv("MODELSYNC_ORACLE_URL");
    String user = System.getenv("MODELSYNC_ORACLE_USER");
    String password = System.getenv("MODELSYNC_ORACLE_PASSWORD");
    assertNotNull(url); assertNotNull(user); assertNotNull(password);
    assertTrue(user.matches("MS_TEST_[A-Za-z0-9_]{1,20}"), "Use a dedicated MS_TEST_ schema");
    var source = new DriverManagerDataSource(url, user, password);
    source.setDriverClassName("oracle.jdbc.OracleDriver");
    var jdbc = new JdbcTemplate(source);
    var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    String binding = "scale-" + UUID.randomUUID();
    assertEquals(0, jdbc.queryForObject("select count(*) from MS_WORK", Integer.class),
        "Scale fixture requires an empty disposable ledger");
    String oracleVersion;
    try (var connection = source.getConnection()) {
      assertEquals(user.toUpperCase(), connection.getSchema().toUpperCase());
      oracleVersion = connection.getMetaData().getDatabaseProductVersion();
    }
    try {
      String insert = "insert into MS_WORK"
          + " (work_id,binding_id,tenant_id,subject_id,priority,generation,attempts,force_refresh,work_status,next_at_ms,requested_at_ms)"
          + " values (?,?,?,?,?,1,0,0,'WAITING',0,0)";
      List<Object[]> batch = new ArrayList<>(1000);
      for (int i = 1; i <= ROWS; i++) {
        var key = new io.modelsync.core.domain.WorkKey(binding,
            new io.modelsync.core.domain.SubjectKey("synthetic", Integer.toString(i)));
        batch.add(new Object[] {key.storageId(), binding, "synthetic", key.subject().id(), Priority.NORMAL.ordinal()});
        if (batch.size() == 1000) { jdbc.batchUpdate(insert, batch); batch.clear(); }
      }
      if (!batch.isEmpty()) jdbc.batchUpdate(insert, batch);
      int actual = jdbc.queryForObject("select count(*) from MS_WORK where binding_id=?", Integer.class, binding);
      assertEquals(ROWS, actual);
      JdbcWorkStore store = new JdbcWorkStore(jdbc);
      store.verifySchema();
      long started = System.nanoTime();
      long[] work = tx.execute(status -> {
        long before = jdbc.queryForObject(SESSION_WORK, Long.class);
        var lease = store.claim(Priority.NORMAL, Instant.now(), Duration.ofSeconds(60)).orElseThrow();
        long after = jdbc.queryForObject(SESSION_WORK, Long.class);
        assertEquals(binding, lease.key().binding());
        assertEquals(lease.key().storageId(), jdbc.queryForObject(
            "select work_id from MS_WORK where lease_token=?", String.class, lease.token()));
        assertEquals("LEASED", jdbc.queryForObject(
            "select work_status from MS_WORK where work_id=?", String.class, lease.key().storageId()));
        assertEquals(lease.token(), jdbc.queryForObject(
            "select lease_token from MS_WORK where work_id=?", String.class, lease.key().storageId()));
        assertEquals(lease.generation(), jdbc.queryForObject(
            "select leased_generation from MS_WORK where work_id=?", Long.class, lease.key().storageId()));
        assertTrue(store.lockValid(lease, Instant.now()));
        return new long[] {before, after};
      });
      long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
      long blocks = work[1] - work[0];
      assertTrue(blocks >= 0, "Oracle session work counters must be monotonic");
      Path report = Path.of("target", "qm", "oracle-scale.json");
      Files.createDirectories(report.getParent());
      Files.writeString(report, "{\"rows\":" + actual
          + ",\"due_rows\":" + actual + ",\"ledger_claims\":1"
          + ",\"db_work_per_ledger_claim\":" + blocks
          + ",\"work_unit\":" + json("Oracle session block gets + consistent gets + physical reads")
          + ",\"claim_elapsed_ms\":" + elapsedMillis
          + ",\"oracle_version\":" + json(oracleVersion) + "}\n");
    } finally {
      // Scope cleanup to the binding generated by this test after the empty-schema assertion.
      jdbc.update("delete from MS_STATE where work_id in (select work_id from MS_WORK where binding_id=?)", binding);
      jdbc.update("delete from MS_WORK where binding_id=?", binding);
    }
  }

  private static String json(String value) {
    StringBuilder out = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '"' || c == '\\') out.append('\\').append(c);
      else if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
      else out.append(c);
    }
    return out.append('"').toString();
  }
}
