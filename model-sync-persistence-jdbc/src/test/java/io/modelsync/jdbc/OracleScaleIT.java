package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.Priority;
import io.modelsync.core.domain.SubjectKey;
import io.modelsync.core.domain.WorkKey;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Actual claim plans/counters on 100k records; never labels a ledger claim an owner admission. */
class OracleScaleIT {
  private static final int ROWS = 100_000;

  @Test void dueLedgerClaimHasMeasuredDatabaseWork() throws Exception {
    assertEquals("DEDICATED_TEST_SCHEMA", System.getenv("MODELSYNC_ORACLE_TEST_SCHEMA_ACK"));
    String user = System.getenv("MODELSYNC_ORACLE_USER");
    assertNotNull(user);
    assertTrue(user.matches("MS_TEST_[A-Za-z0-9_]{1,20}"));
    var source = new DriverManagerDataSource(System.getenv("MODELSYNC_ORACLE_URL"), user,
        System.getenv("MODELSYNC_ORACLE_PASSWORD"));
    source.setDriverClassName("oracle.jdbc.OracleDriver");
    // One physical connection retains the same session population for seed/claim/cleanup deltas.
    try (var connection = source.getConnection()) {
      assertEquals(user.toUpperCase(), connection.getSchema().toUpperCase());
      var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      var tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
      assertEquals(0, jdbc.queryForObject("select count(*) from MS_WORK", Integer.class),
          "Scale fixture requires an empty disposable ledger");
      String binding = "scale-" + UUID.randomUUID();
      Map<String, Object> report = new LinkedHashMap<>();
      report.put("rows", ROWS);
      report.put("oracle_version", connection.getMetaData().getDatabaseProductVersion());
      report.put("scope", "ledger-claim-diagnostics-not-provider-admission");
      report.put("physical_connections", 1);
      List<Map<String, Object>> distributions = new ArrayList<>();
      report.put("distributions", distributions);
      Path path = Path.of("target/qm/oracle-scale.json");
      try {
        var seedBefore = OracleMeasurements.snapshot(jdbc);
        long seedStarted = System.nanoTime();
        String insert = "insert into MS_WORK (work_id,binding_id,tenant_id,subject_id,priority,"
            + "generation,attempts,force_refresh,work_status,next_at_ms,requested_at_ms)"
            + " values (?,?,?,?,?,1,0,0,'WAITING',0,0)";
        List<Object[]> batch = new ArrayList<>(1000);
        for (int i = 1; i <= ROWS; i++) {
          var key = new WorkKey(binding, new SubjectKey("synthetic", Integer.toString(i)));
          batch.add(new Object[] {key.storageId(), binding, "synthetic", key.subject().id(), Priority.NORMAL.ordinal()});
          if (batch.size() == 1000) {
            tx.executeWithoutResult(status -> jdbc.batchUpdate(insert, batch));
            batch.clear();
          }
        }
        assertEquals(ROWS, jdbc.queryForObject("select count(*) from MS_WORK", Integer.class));
        report.put("seed_ms", (System.nanoTime() - seedStarted) / 1_000_000);
        report.put("seed_work", OracleMeasurements.difference(seedBefore, OracleMeasurements.snapshot(jdbc)));
        jdbc.execute("alter session set statistics_level=all");
        report.put("statistics_level", "ALL");
        report.put("table_statistics", jdbc.queryForList("select table_name,num_rows,blocks,last_analyzed,stale_stats"
            + " from user_tab_statistics where table_name='MS_WORK'"));
        report.put("index_statistics", jdbc.queryForList("select index_name,num_rows,leaf_blocks,clustering_factor,last_analyzed"
            + " from user_indexes where table_name='MS_WORK' order by index_name"));
        var store = new JdbcWorkStore(jdbc);
        store.verifySchema();
        for (String distribution : List.of("all-due", "due-future", "expired-future", "mixed", "all-future",
            "all-due-with-stats", "mixed-with-stats", "all-future-with-stats")) {
          long setupStarted = System.nanoTime();
          var setupBefore = OracleMeasurements.snapshot(jdbc);
          if (!distribution.equals("all-due")) configure(jdbc, binding, distribution.replace("-with-stats", ""));
          Map<String, Object> measured = new LinkedHashMap<>();
          measured.put("distribution", distribution);
          measured.put("setup_ms", (System.nanoTime() - setupStarted) / 1_000_000);
          measured.put("setup_work", OracleMeasurements.difference(setupBefore, OracleMeasurements.snapshot(jdbc)));
          if (distribution.endsWith("-with-stats")) {
            var statsBefore = OracleMeasurements.snapshot(jdbc);
            long statsStarted = System.nanoTime();
            jdbc.execute("begin dbms_stats.gather_table_stats(ownname=>user,tabname=>'MS_WORK',cascade=>true,"
                + "estimate_percent=>dbms_stats.auto_sample_size,method_opt=>'FOR ALL COLUMNS SIZE 1',no_invalidate=>false); end;");
            measured.put("gather_statistics_ms", (System.nanoTime() - statsStarted) / 1_000_000);
            measured.put("gather_statistics_work", OracleMeasurements.difference(statsBefore, OracleMeasurements.snapshot(jdbc)));
          }
          measured.put("table_statistics", jdbc.queryForList("select table_name,num_rows,blocks,last_analyzed,stale_stats from user_tab_statistics where table_name='MS_WORK'"));
          long now = System.currentTimeMillis();
          measured.put("population", jdbc.queryForList("select work_status,case when next_at_ms<=? then 'DUE' else 'FUTURE' end schedule,"
              + "count(*) rows_count from MS_WORK where binding_id=? group by work_status,case when next_at_ms<=? then 'DUE' else 'FUTURE' end",
              now, binding, now));
          measured.put("waiting_due_rows", jdbc.queryForObject("select count(*) from MS_WORK where binding_id=? and work_status='WAITING' and next_at_ms<=?", Long.class, binding, now));
          measured.put("future_waiting_rows", jdbc.queryForObject("select count(*) from MS_WORK where binding_id=? and work_status='WAITING' and next_at_ms>?", Long.class, binding, now));
          measured.put("expired_lease_rows", jdbc.queryForObject("select count(*) from MS_WORK where binding_id=? and work_status='LEASED' and lease_until_ms<=?", Long.class, binding, now));
          List<Map<String, Object>> claims = new ArrayList<>();
          measured.put("samples", claims);
          distributions.add(measured);
          var cursorBefore = OracleMeasurements.sql(jdbc);
          for (int sample = 0; sample < 3; sample++) {
            Map<String, Object> claim = new LinkedHashMap<>();
            var before = OracleMeasurements.snapshot(jdbc);
            long started = System.nanoTime();
            final boolean commitLease = distribution.equals("all-due") && sample == 0;
            tx.executeWithoutResult(status -> {
              long claimStarted = System.nanoTime();
              var lease = store.claim(Priority.NORMAL, Instant.now(), Duration.ofSeconds(60));
              claim.put("claim_only_elapsed_ms", (System.nanoTime() - claimStarted) / 1_000_000);
              claim.put("claim_work", OracleMeasurements.difference(before, OracleMeasurements.snapshot(jdbc)));
              assertEquals(!distribution.startsWith("all-future"), lease.isPresent());
              claim.put("ledger_claims", lease.isPresent() ? 1 : 0);
              lease.ifPresent(value -> {
                assertEquals(binding, value.key().binding());
                assertEquals("LEASED", jdbc.queryForObject("select work_status from MS_WORK where work_id=?", String.class, value.key().storageId()));
                assertEquals(value.token(), jdbc.queryForObject("select lease_token from MS_WORK where work_id=?", String.class, value.key().storageId()));
                assertEquals(value.generation(), jdbc.queryForObject("select leased_generation from MS_WORK where work_id=?", Long.class, value.key().storageId()));
                assertTrue(store.lockValid(value, Instant.now()));
              });
              if (!commitLease) status.setRollbackOnly();
            });
            claim.put("claim_elapsed_ms", (System.nanoTime() - started) / 1_000_000);
            claim.put("committed_lease", commitLease);
            claims.add(claim);
            if (commitLease) {
              report.put("due_rows", ROWS);
              report.put("ledger_claims", 1);
              report.put("claim_elapsed_ms", claim.get("claim_elapsed_ms"));
              @SuppressWarnings("unchecked") var counters = (Map<String, Long>)
                  ((Map<String, Object>) claim.get("claim_work")).get("session_statistics");
              report.put("db_work_per_ledger_claim", counters.get("db block gets")
                  + counters.get("consistent gets") + counters.get("physical reads"));
              report.put("work_unit", "Oracle db block gets + consistent gets + physical reads; individual counters retained");
              assertEquals(1, jdbc.queryForObject("select count(*) from MS_WORK where binding_id=? and work_status='LEASED' and lease_token is not null and leased_generation=generation",
                  Integer.class, binding), "First lease must be visible after commit");
            }
          }
          measured.put("claim_plans", OracleMeasurements.claimPlans(jdbc, cursorBefore));
          OracleMeasurements.write(path, report);
        }
      } finally {
        long cleanupStarted = System.nanoTime();
        var cleanupBefore = OracleMeasurements.snapshot(jdbc);
        jdbc.update("delete from MS_STATE where work_id in (select work_id from MS_WORK where binding_id=?)", binding);
        jdbc.update("delete from MS_WORK where binding_id=?", binding);
        report.put("cleanup_ms", (System.nanoTime() - cleanupStarted) / 1_000_000);
        report.put("cleanup_work", OracleMeasurements.difference(cleanupBefore, OracleMeasurements.snapshot(jdbc)));
        report.put("cleanup_rows_remaining", jdbc.queryForObject("select count(*) from MS_WORK where binding_id=?", Integer.class, binding));
        OracleMeasurements.write(path, report);
      }
    }
  }

  private static void configure(JdbcTemplate jdbc, String binding, String distribution) {
    long future = Instant.now().plus(Duration.ofDays(1)).toEpochMilli();
    int waiting = distribution.equals("all-due") ? ROWS : distribution.equals("due-future") ? 10_000
        : distribution.equals("mixed") ? 5_000 : 0;
    int expired = distribution.equals("expired-future") ? 10_000 : distribution.equals("mixed") ? 5_000 : 0;
    jdbc.update("update MS_WORK set work_status=case when to_number(subject_id)>? and to_number(subject_id)<=? then 'LEASED' else 'WAITING' end,"
        + "next_at_ms=case when to_number(subject_id)<=? then 0 else ? end,"
        + "lease_until_ms=case when to_number(subject_id)>? and to_number(subject_id)<=? then 0 else null end,"
        + "lease_token=case when to_number(subject_id)>? and to_number(subject_id)<=? then 'expired-test-lease' else null end,leased_generation=generation where binding_id=?",
        waiting, waiting + expired, waiting + expired, future, waiting, waiting + expired,
        waiting, waiting + expired, binding);
  }
}
