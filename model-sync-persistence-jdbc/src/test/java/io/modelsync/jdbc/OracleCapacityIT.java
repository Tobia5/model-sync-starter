package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;
import io.modelsync.spring.SpringLocalTransactions;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** A bounded, multi-worker Oracle reconciliation workload with one million durable intents. */
class OracleCapacityIT {
  private static final int ROWS = 1_000_000;
  private static final int DUE = 100_000;
  private static final int WORKERS = 4;
  private static final int OWNER_CALL_TARGET = 230;
  private static final String SESSION_WORK = "select coalesce(sum(s.value),0) from v$sesstat s"
      + " join v$statname n on n.statistic#=s.statistic#"
      + " join v$session x on x.sid=s.sid"
      + " where x.username=user and n.name in ('db block gets','consistent gets','physical reads')";

  @Test void millionRecordReconciliationMeasuresCompletedWork() throws Exception {
    assertEquals("DEDICATED_TEST_SCHEMA", System.getenv("MODELSYNC_ORACLE_TEST_SCHEMA_ACK"));
    String url = System.getenv("MODELSYNC_ORACLE_URL");
    String user = System.getenv("MODELSYNC_ORACLE_USER");
    String password = System.getenv("MODELSYNC_ORACLE_PASSWORD");
    assertNotNull(url); assertNotNull(user); assertNotNull(password);
    assertTrue(user.matches("MS_TEST_[A-Za-z0-9_]{1,20}"));
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(url); config.setUsername(user); config.setPassword(password);
    config.setDriverClassName("oracle.jdbc.OracleDriver");
    config.setMaximumPoolSize(8); config.setMinimumIdle(8);
    // Keep this bounded measurement's session-counter population stable; close all eight at test exit.
    config.setMaxLifetime(0);
    config.setConnectionInitSql("alter session set statistics_level=all");
    config.setPoolName("modelsync-capacity");
    try (HikariDataSource source = new HikariDataSource(config)) {
      SelectorJdbcTemplate jdbc = new SelectorJdbcTemplate(source);
      assertEquals(0, jdbc.queryForObject("select count(*) from MS_WORK", Integer.class),
          "Capacity fixture requires an empty dedicated ledger");
      assertEquals(0, jdbc.queryForObject("select count(*) from user_tables where table_name='MS_TEST_PROJECTION'", Integer.class),
          "Test-only projection table must be absent");
      String binding = "capacity-" + UUID.randomUUID();
      String quotaGroup = "capacity-" + UUID.randomUUID();
      String oracleVersion;
      try (var connection = source.getConnection()) {
        assertEquals(user.toUpperCase(), connection.getSchema().toUpperCase());
        oracleVersion = connection.getMetaData().getDatabaseProductVersion();
      }
      jdbc.execute("create table MS_TEST_PROJECTION (tenant_id varchar2(128 char) not null,"
          + " subject_id varchar2(256 char) not null, projection_value varchar2(512 char) not null,"
          + " revision varchar2(36 char) not null, expires_at_ms number(19),"
          + " primary key (tenant_id,subject_id))");
      Map<String, Object> phases = new LinkedHashMap<>();
      phases.put("scope", "oracle-backed-reconciliation-phase-diagnostics");
      phases.put("statistics_level", "ALL");
      phases.put("pool_max_connections", 8);
      Path phaseReport = Path.of("target/qm/oracle-workload-phases.json");
      try {
        long requestedAt = System.currentTimeMillis();
        var seedBefore = OracleMeasurements.snapshot(jdbc);
        long seedStarted = System.nanoTime();
        seed(jdbc, binding, requestedAt);
        long seedElapsedMs = (System.nanoTime() - seedStarted) / 1_000_000;
        phases.put("seed_ms", seedElapsedMs);
        phases.put("seed_work", OracleMeasurements.difference(seedBefore, OracleMeasurements.snapshot(jdbc)));
        OracleMeasurements.write(phaseReport, phases);
        assertEquals(ROWS, jdbc.queryForObject("select count(*) from MS_WORK where binding_id=?", Integer.class, binding));
        assertEquals(DUE, jdbc.queryForObject("select count(*) from MS_WORK where binding_id=? and next_at_ms=0", Integer.class, binding));
        var manager = new DataSourceTransactionManager(source);
        var tx = new SpringLocalTransactions(manager);
        var store = new CountedWorkStore(new JdbcWorkStore(jdbc));
        var quota = new JdbcQuotaGate(jdbc, manager, Clock.systemUTC());
        quota.register(quotaGroup, Duration.ofMillis(20)); // 50 physical calls/s, shared by all workers.
        var measuredQuota = new MeasuredQuotaGate(quota);
        AtomicInteger ownerCalls = new AtomicInteger();
        AtomicInteger limited = new AtomicInteger();
        AtomicInteger timedOut = new AtomicInteger();
        var projection = new OracleTestProjectionStore(jdbc);
        var bindingValue = ModelBinding.builder(binding, String.class)
            .boundary("synthetic-oracle-capacity")
            .owner("instrumented-local-owner", request -> {
              assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                  "Owner call ran inside the local transaction");
              int call = ownerCalls.incrementAndGet();
              Thread.sleep(call % 3 == 0 ? 25 : 5);
              if (call % 29 == 0) { timedOut.incrementAndGet(); throw new SocketTimeoutException("synthetic timeout"); }
              if (call % 17 == 0) { limited.incrementAndGet(); throw new RateLimitedAfter(Duration.ofMillis(100)); }
              return FetchResult.found("source-" + request.key().id(), "v1");
            })
            .replica(projection).repairFromOwner()
            .fingerprint(Digests::sha256Fields).quota(quotaGroup)
            .freshness(new FreshnessPolicy(Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofHours(1)))
            .build();
        var registry = new ModelRegistry(List.of(bindingValue));
        var gateway = new SyncGateway(registry, store, tx, Clock.systemUTC());
        for (int i = 0; i < 100; i++)
          gateway.request(binding, "synthetic", "1", Priority.URGENT, true);
        assertEquals(ROWS, jdbc.queryForObject("select count(*) from MS_WORK", Integer.class),
            "Hot requests must coalesce to one durable key");
        var coordinator = new SyncCoordinator(registry, store, tx, measuredQuota, Clock.systemUTC(),
            SyncOptions.defaults(), WeightedPriorities.defaults(), () -> 0.5);
        // Warm the pool and JIT with bounded real work; measurement starts afterwards.
        var warmupBefore = OracleMeasurements.snapshot(jdbc);
        long warmupStarted = System.nanoTime();
        for (int i = 0; i < 10; i++) coordinator.runOne();
        long warmupElapsedMs = (System.nanoTime() - warmupStarted) / 1_000_000;
        long warmupCalls = ownerCalls.get();
        long warmupCompleted = jdbc.queryForObject("select count(*) from MS_STATE s join MS_WORK w on w.work_id=s.work_id where w.binding_id=? and s.outcome in ('MATCH','APPLIED')", Long.class, binding);
        phases.put("warmup_work", OracleMeasurements.difference(warmupBefore, OracleMeasurements.snapshot(jdbc)));
        var measurementBefore = OracleMeasurements.snapshot(jdbc);
        long grantsBefore = measuredQuota.granted.get();
        long denialsBefore = measuredQuota.denied.get();
        long cooldownsBefore = measuredQuota.cooldowns.get();
        long selectorQueriesBefore = jdbc.selectorQueries.get();
        long selectionsBefore = store.selections.get();
        long emptySelectionsBefore = store.empty.get();
        long beforeWork = jdbc.queryForObject(SESSION_WORK, Long.class);
        long started = System.nanoTime();
        long deadline = started + Duration.ofMinutes(3).toNanos();
        AtomicLong attempts = new AtomicLong();
        AtomicLong polls = new AtomicLong();
        AtomicLong emptyPolls = new AtomicLong();
        List<Long> claimedLatencies = Collections.synchronizedList(new ArrayList<>());
        List<Long> emptyLatencies = Collections.synchronizedList(new ArrayList<>());
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        try (var workers = Executors.newFixedThreadPool(WORKERS)) {
          List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
          for (int i = 0; i < WORKERS; i++) futures.add(workers.submit(() -> {
            while (ownerCalls.get() < OWNER_CALL_TARGET && System.nanoTime() < deadline) {
              long callStarted = System.nanoTime();
              boolean claimed = coordinator.runOne();
              polls.incrementAndGet();
              long latency = (System.nanoTime() - callStarted) / 1_000_000;
              if (claimed) { attempts.incrementAndGet(); claimedLatencies.add(latency); }
              else { emptyPolls.incrementAndGet(); emptyLatencies.add(latency); }
              latencies.add(latency);
              try { Thread.sleep(5); }
              catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
          }));
          for (var future : futures) future.get(200, TimeUnit.SECONDS);
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        long dbWork = jdbc.queryForObject(SESSION_WORK, Long.class) - beforeWork;
        phases.put("measurement_work", OracleMeasurements.difference(measurementBefore, OracleMeasurements.snapshot(jdbc)));
        @SuppressWarnings("unchecked") var previousSql = (List<Map<String, Object>>) measurementBefore.get("sql_cursors");
        phases.put("claim_plans", OracleMeasurements.claimPlans(jdbc, previousSql));
        phases.put("table_statistics", jdbc.queryForList("select table_name,num_rows,blocks,to_char(last_analyzed,'YYYY-MM-DD\"T\"HH24:MI:SS') last_analyzed_db_local,stale_stats from user_tab_statistics where table_name='MS_WORK'"));
        phases.put("index_statistics", jdbc.queryForList("select index_name,num_rows,leaf_blocks,clustering_factor,to_char(last_analyzed,'YYYY-MM-DD\"T\"HH24:MI:SS') last_analyzed_db_local from user_indexes where table_name='MS_WORK' order by index_name"));
        long measuredGrants = measuredQuota.granted.get() - grantsBefore;
        long measuredDenials = measuredQuota.denied.get() - denialsBefore;
        phases.put("measurement_ms", elapsedMs);
        phases.put("selector_queries", jdbc.selectorQueries.get() - selectorQueriesBefore);
        phases.put("polls", polls.get());
        phases.put("empty_polls", emptyPolls.get());
        phases.put("selection_attempts", store.selections.get() - selectionsBefore);
        phases.put("empty_selections", store.empty.get() - emptySelectionsBefore);
        phases.put("measured_quota_grants", measuredGrants);
        phases.put("measured_quota_denials", measuredDenials);
        phases.put("measured_provider_cooldowns", measuredQuota.cooldowns.get() - cooldownsBefore);
        phases.put("measured_ledger_attempts", attempts.get());
        OracleMeasurements.write(phaseReport, phases);
        long completed = jdbc.queryForObject("select count(*) from MS_STATE s join MS_WORK w on w.work_id=s.work_id where w.binding_id=? and s.outcome in ('MATCH','APPLIED')", Long.class, binding);
        long measuredCompleted = completed - warmupCompleted;
        long measuredOwnerCalls = ownerCalls.get() - warmupCalls;
        long projectionRows = jdbc.queryForObject("select count(*) from MS_TEST_PROJECTION", Long.class);
        List<Long> lag = jdbc.query("select s.reconciled_at_ms-w.requested_at_ms from MS_STATE s join MS_WORK w on w.work_id=s.work_id where w.binding_id=? and s.outcome in ('MATCH','APPLIED')",
            (r, n) -> r.getLong(1), binding);
        Path report = Path.of("target", "qm", "oracle-workload.json");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "{\"scope\":\"oracle-backed-reconciliation\",\"rows\":" + ROWS
            + ",\"seeded_rows\":" + ROWS + ",\"seed_ms\":" + seedElapsedMs
            + ",\"due_rows\":" + DUE + ",\"hot_requests\":100,\"workers\":" + WORKERS
            + ",\"owner_spacing_ms\":20,\"owner_latency_ms\":{\"ordinary\":5,\"slow\":25}"
            + ",\"warmup_owner_calls\":" + warmupCalls + ",\"warmup_completed_syncs\":" + warmupCompleted
            + ",\"warmup_ms\":" + warmupElapsedMs
            + ",\"measured_owner_calls\":" + measuredOwnerCalls
            + ",\"physical_owner_calls\":" + ownerCalls.get() + ",\"provider_429\":" + limited.get()
            + ",\"synthetic_timeouts\":" + timedOut.get() + ",\"ledger_attempts\":" + attempts.get()
            + ",\"completed_syncs\":" + completed + ",\"measured_completed_syncs\":" + measuredCompleted
            + ",\"owner_calls_per_second\":" + (1000.0 * measuredOwnerCalls / Math.max(1, elapsedMs))
            + ",\"completed_syncs_per_second\":" + (1000.0 * measuredCompleted / Math.max(1, elapsedMs))
            + ",\"owner_failure_rate\":" + (ownerCalls.get() == 0 ? "null" : Double.toString((double) (limited.get()+timedOut.get()) / ownerCalls.get()))
            + ",\"projection_rows\":" + projectionRows
            + ",\"db_session_work\":" + dbWork + ",\"db_work_per_measured_owner_call\":"
            + (measuredOwnerCalls == 0 ? "null" : Double.toString((double) dbWork / measuredOwnerCalls))
            + ",\"run_one_latency_ms\":" + percentiles(latencies)
            + ",\"reconciliation_lag_ms\":" + percentiles(lag)
            + ",\"measurement_ms\":" + elapsedMs + ",\"jvm_max_heap_bytes\":" + Runtime.getRuntime().maxMemory()
            + ",\"jvm_used_heap_bytes\":" + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())
            + ",\"available_processors\":" + Runtime.getRuntime().availableProcessors()
            + ",\"pool_max_connections\":8,\"pool_max_lifetime_ms\":0,\"oracle_version\":" + quote(oracleVersion) + "}\n");
        @SuppressWarnings("unchecked") Map<String, Object> workload =
            tools.jackson.databind.json.JsonMapper.builder().build().readValue(Files.readString(report), Map.class);
        workload.put("selector_queries", jdbc.selectorQueries.get() - selectorQueriesBefore);
        workload.put("polls", polls.get());
        workload.put("empty_polls", emptyPolls.get());
        workload.put("selection_attempts", store.selections.get() - selectionsBefore);
        workload.put("empty_selections", store.empty.get() - emptySelectionsBefore);
        workload.put("claimed_run_one_latency_ms", tools.jackson.databind.json.JsonMapper.builder().build().readValue(percentiles(claimedLatencies), Map.class));
        workload.put("empty_poll_latency_ms", tools.jackson.databind.json.JsonMapper.builder().build().readValue(percentiles(emptyLatencies), Map.class));
        workload.put("measured_quota_grants", measuredGrants);
        workload.put("measured_quota_denials", measuredDenials);
        workload.put("db_work_per_ledger_attempt", attempts.get() == 0 ? null : (double) dbWork / attempts.get());
        workload.put("db_work_per_completed_sync", measuredCompleted == 0 ? null : (double) dbWork / measuredCompleted);
        workload.put("phase_diagnostics", phases);
        OracleMeasurements.write(report, workload);
        assertTrue(ownerCalls.get() >= OWNER_CALL_TARGET, "Owner-call target not reached before bounded deadline");
        assertTrue(completed >= 150, "Too few completed reconciliations for the declared workload");
        assertEquals(completed, projectionRows, "Accepted work is not a completed projection");
        assertEquals(completed, lag.size());
        assertTrue(dbWork >= 0);
        assertTrue(measuredCompleted > 0 && measuredOwnerCalls > 0);
        assertEquals(measuredOwnerCalls, measuredGrants, "Each admission must have exactly one controlled owner entry");
        assertEquals(attempts.get(), measuredGrants + measuredDenials, "Separate quota-denial churn from admitted work");
        assertTrue(limited.get() > 0 && timedOut.get() > 0, "Failure injection was not exercised");
      } finally {
        var cleanupBefore = OracleMeasurements.snapshot(jdbc);
        long cleanupStarted = System.nanoTime();
        jdbc.update("delete from MS_STATE where work_id in (select work_id from MS_WORK where binding_id=?)", binding);
        jdbc.update("delete from MS_WORK where binding_id=?", binding);
        jdbc.update("delete from MS_QUOTA where quota_group=?", quotaGroup);
        jdbc.execute("drop table MS_TEST_PROJECTION purge");
        phases.put("cleanup_ms", (System.nanoTime() - cleanupStarted) / 1_000_000);
        phases.put("cleanup_work", OracleMeasurements.difference(cleanupBefore, OracleMeasurements.snapshot(jdbc)));
        phases.put("cleanup_rows_remaining", jdbc.queryForObject("select count(*) from MS_WORK where binding_id=?", Integer.class, binding));
        OracleMeasurements.write(phaseReport, phases);
      }
    }
  }

  private static void seed(JdbcTemplate jdbc, String binding, long requestedAt) {
    var seedTx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    String sql = "insert into MS_WORK (work_id,binding_id,tenant_id,subject_id,priority,generation,attempts,force_refresh,work_status,next_at_ms,requested_at_ms) values (?,?,?,?,?,1,0,0,'WAITING',?,?)";
    List<Object[]> batch = new ArrayList<>(2000);
    for (int i = 1; i <= ROWS; i++) {
      WorkKey key = new WorkKey(binding, new SubjectKey("synthetic", Integer.toString(i)));
      batch.add(new Object[] {key.storageId(), binding, "synthetic", key.subject().id(),
          Priority.NORMAL.ordinal(), i <= DUE ? 0L : requestedAt + Duration.ofDays(1).toMillis(), requestedAt});
      if (batch.size() == 2000) {
        seedTx.executeWithoutResult(status -> jdbc.batchUpdate(sql, batch));
        batch.clear();
        if (i % 100_000 == 0) System.out.println("Oracle capacity fixture seeded " + i + " rows");
      }
    }
    if (!batch.isEmpty()) seedTx.executeWithoutResult(status -> jdbc.batchUpdate(sql, batch));
  }

  private static String percentiles(List<Long> values) {
    if (values.isEmpty()) return "null";
    List<Long> sorted = new ArrayList<>(values);
    Collections.sort(sorted);
    return "{\"p50\":" + point(sorted, 0.50) + ",\"p95\":" + point(sorted, 0.95)
        + ",\"p99\":" + point(sorted, 0.99) + "}";
  }

  private static long point(List<Long> sorted, double fraction) {
    return sorted.get(Math.min(sorted.size()-1, (int) Math.ceil(sorted.size()*fraction)-1));
  }

  private static String quote(String value) {
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
