package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariDataSource;
import io.modelsync.autoconfigure.ModelSyncProperties;
import io.modelsync.core.Priority;
import io.modelsync.core.application.SyncGateway;
import io.modelsync.core.domain.SubjectKey;
import io.modelsync.core.domain.WorkKey;
import io.modelsync.reactor.ReactorSyncWorker;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/** Actual auto-configured continuous worker; not the direct runOne/million-row capacity test. */
class OracleWorkerIT {
  private static final long WAKE_BUDGET_MS = 2500;
  private ConfigurableApplicationContext context;
  private ReactorSyncWorker worker;
  private WorkerJdbcTemplate jdbc;
  private WorkerOwner owner;
  private WorkerQuota quota;
  private SyncGateway gateway;
  private final Map<String, Object> report = new LinkedHashMap<>();
  private final List<Map<String, Object>> phases = new ArrayList<>();

  @Test void autoConfiguredWorkerPacesBacklogAndBoundsIdleRechecks() throws Exception {
    report.put("scope", "actual-auto-configured-reactor-worker-not-million-row-capacity");
    report.put("status", "FAILED"); report.put("phases", phases);
    report.put("workers", 4); report.put("pool", 8); report.put("persistence_threads", 4);
    report.put("owner_threads", 8); report.put("queue_per_thread", 64);
    report.put("owner_spacing_ms", 20); report.put("owner_latency_ms", 5);
    report.put("measurement_idle_ms", 4000); report.put("idle_warmup_ms", 3000);
    report.put("wake_budget_ms", WAKE_BUDGET_MS); report.put("idle_ticks_per_second_budget", 6);
    report.put("selector_callbacks_per_tick_bound", 9);
    report.put("idle_logical_gets_per_second_budget", 100000);
    report.put("counter_scope", "stable eight runtime pool sessions; includes observation SQL; excludes exited enqueue process");
    report.put("cache_control", "no global flush; fresh host/pool per variant; warmup and seed excluded; table/index history retained");
    try {
      for (boolean adaptive : List.of(false, true)) {
        try {
          open(adaptive);
          productive(adaptive ? "bounded-productive" : "fixed-productive");
          idle(adaptive ? "bounded-empty" : "fixed-empty", adaptive);
          if (adaptive) {
            future();
            futureDeadline();
            wake(false);
            wake(true);
            cooldownRecovery();
          }
        } finally { close(); }
      }
      report.put("status", "PASS");
    } finally { WorkerMeasurements.write(report); }
  }

  private void open(boolean adaptive) {
    context = new SpringApplicationBuilder(WorkerRuntimeConfiguration.class)
        .web(WebApplicationType.NONE).profiles("worker-oracle")
        .run("--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.open-in-view=false",
            "--model-sync.enabled=true", "--model-sync.worker-enabled=true", "--model-sync.aop.enabled=false",
            "--model-sync.quotas.worker-main=20ms", "--model-sync.quotas.worker-other=1ms",
            "--model-sync.productive-interval=" + (adaptive ? "5ms" : "250ms"),
            "--model-sync.max-idle-interval=" + (adaptive ? "1s" : "250ms"));
    worker = context.getBean(ReactorSyncWorker.class); jdbc = context.getBean(WorkerJdbcTemplate.class);
    owner = context.getBean(WorkerOwner.class); quota = context.getBean(WorkerQuota.class);
    gateway = context.getBean(SyncGateway.class);
    assertTrue(worker.isRunning());
    assertEquals("modelSyncWorker", context.getBeanNamesForType(ReactorSyncWorker.class)[0]);
    assertEquals("modelSyncWorker", context.getBeanFactory().getBeanDefinition("modelSyncWorker").getFactoryMethodName());
    assertInstanceOf(JpaTransactionManager.class, context.getBean(PlatformTransactionManager.class));
    assertSame(context.getBean(HikariDataSource.class), ((JpaTransactionManager) context.getBean(PlatformTransactionManager.class)).getDataSource());
    var p = context.getBean(ModelSyncProperties.class);
    assertEquals(4, p.getConcurrency()); assertEquals(Duration.ofMillis(250), p.getPollInterval());
    stop();
  }

  private void productive(String name) throws Exception {
    for (int i = 0; i < 10; i++) enqueue("warm-" + i);
    worker.start();
    await(() -> completed() == 10, 15000); stop();
    for (int i = 0; i < 160; i++) enqueue("backlog-" + i);
    var phase = begin(name);
    phase.put("seed_rows", 160); phase.put("warmup_completions", 10);
    worker.start();
    await(() -> completed() == 170, 20000);
    end(phase);
    @SuppressWarnings("unchecked") var counts = (Map<String, Long>) phase.get("scheduling_delta");
    assertEquals(160L, counts.get("completed_syncs"));
    assertEquals(160L, counts.get("owner_calls"));
    assertEquals(160L, counts.get("quota_grants"));
    assertTrue(worker.activity().maximumInFlight() <= 4);
    assertTrue(owner.maximum.get() <= 4);
    phase.put("status", "PASS"); WorkerMeasurements.write(report);
  }

  private void idle(String name, boolean adaptive) throws Exception {
    clear(); worker.start(); Thread.sleep(3000);
    var phase = begin(name);
    Thread.sleep(4000); end(phase);
    @SuppressWarnings("unchecked") var counts = (Map<String, Long>) phase.get("scheduling_delta");
    assertEquals(0L, counts.get("owner_calls")); assertEquals(0L, counts.get("quota_grants"));
    assertEquals(0L, counts.get("quota_denials"));
    double seconds = ((Number) phase.get("elapsed_ms")).doubleValue() / 1000;
    assertTrue(counts.get("ticks") <= (adaptive ? 6 : 18) * seconds + 4, "Bounded idle tick budget");
    assertTrue(counts.get("selector_callbacks") <= 9 * counts.get("ticks") + 36);
    if (adaptive) assertTrue(((Number) phase.get("logical_gets_per_second")).doubleValue() <= 100000);
    phase.put("status", "PASS"); WorkerMeasurements.write(report);
  }

  private void future() throws Exception {
    for (int i = 0; i < 1000; i++) enqueue("future-" + i);
    jdbc.update("update MS_WORK set next_at_ms=? where binding_id='worker-main'", System.currentTimeMillis() + 3600000);
    idleWithoutClear("bounded-future");
  }

  private void idleWithoutClear(String name) throws Exception {
    worker.start(); Thread.sleep(3000);
    var phase = begin(name); phase.put("future_rows", 1000);
    Thread.sleep(4000); end(phase);
    @SuppressWarnings("unchecked") var counts = (Map<String, Long>) phase.get("scheduling_delta");
    assertEquals(0L, counts.get("owner_calls")); assertEquals(0L, counts.get("quota_grants"));
    double seconds = ((Number) phase.get("elapsed_ms")).doubleValue() / 1000;
    assertTrue(counts.get("ticks") <= 6 * seconds + 4);
    assertTrue(counts.get("selector_callbacks") <= 9 * counts.get("ticks") + 36);
    assertTrue(((Number) phase.get("logical_gets_per_second")).doubleValue() <= 100000);
    phase.put("status", "PASS"); WorkerMeasurements.write(report);
  }

  private void wake(boolean external) throws Exception {
    worker.start(); Thread.sleep(3000);
    var phase = begin(external ? "independent-process-urgent" : "local-urgent");
    String id = external ? "external" : "urgent";
    long committedBefore = System.currentTimeMillis(), committedAfter;
    if (external) {
      var javaCommand = Path.of(System.getProperty("java.home"), "bin", "java").toString();
      var output = WorkerMeasurements.directory().resolve("worker-enqueue-process.log");
      var child = new ProcessBuilder(javaCommand, "-cp", System.getProperty("java.class.path"), WorkerEnqueueProcess.class.getName())
          .redirectErrorStream(true).redirectOutput(output.toFile()).start();
      try {
        assertTrue(child.waitFor(15, TimeUnit.SECONDS), "Independent enqueue process timeout");
        assertEquals(0, child.exitValue());
      } finally { if (child.isAlive()) child.destroyForcibly(); }
      String line = java.nio.file.Files.readAllLines(output).stream()
          .filter(value -> value.startsWith("SYNTHETIC_ENQUEUE_COMMITTED ")).findFirst().orElseThrow();
      String[] values = line.split(" ");
      committedBefore = Long.parseLong(values[1]); committedAfter = Long.parseLong(values[2]);
      phase.put("enqueue_process_exit", 0);
    } else { enqueue(id); committedAfter = System.currentTimeMillis(); }
    await(() -> completedAt(id) > 0, WAKE_BUDGET_MS);
    long completedAt = System.currentTimeMillis(); // first observation of the committed outcome
    phase.put("state_reconciled_at_ms", completedAt(id)); // application timestamp precedes COMMIT
    phase.put("commit_before_ms", committedBefore); phase.put("commit_after_ms", committedAfter);
    phase.put("completion_at_ms", completedAt);
    phase.put("wakeup_latency_upper_ms", Math.max(0, completedAt - committedBefore));
    phase.put("wakeup_latency_lower_ms", Math.max(0, completedAt - committedAfter));
    end(phase);
    assertTrue(completedAt - committedBefore <= WAKE_BUDGET_MS, "Bounded independent/local wakeup");
    phase.put("status", "PASS"); WorkerMeasurements.write(report);
  }

  private void futureDeadline() throws Exception {
    worker.start(); Thread.sleep(3000);
    var phase = begin("future-becomes-due");
    long due = System.currentTimeMillis() + 500;
    jdbc.update("update MS_WORK set next_at_ms=? where work_id=?", due, key("future-0").storageId());
    await(() -> completedAt("future-0") > 0, WAKE_BUDGET_MS + 500);
    long observed = System.currentTimeMillis();
    phase.put("eligible_at_ms", due); phase.put("completion_observed_at_ms", observed);
    phase.put("wakeup_latency_upper_ms", Math.max(0, observed - due)); end(phase);
    assertTrue(observed - due <= WAKE_BUDGET_MS);
    phase.put("status", "PASS"); WorkerMeasurements.write(report);
  }

  private void cooldownRecovery() throws Exception {
    clear(); enqueue("cooldown"); enqueue("recovery");
    long expiry = System.currentTimeMillis() + 1000;
    String recovery = key("recovery").storageId();
    jdbc.update("update MS_WORK set work_status='LEASED',lease_token='abandoned',leased_generation=generation,lease_until_ms=? where work_id=?", expiry, recovery);
    long cooldownUntil = System.currentTimeMillis() + 8000;
    quota.cooldown("worker-main", Instant.ofEpochMilli(cooldownUntil));
    var phase = begin("cooldown-independent-owner-and-recovery");
    gateway.request("worker-other", "synthetic-worker", "other", Priority.URGENT, true);
    long enqueued = System.currentTimeMillis(); worker.start();
    await(() -> completedAt("other", "worker-other") > 0, WAKE_BUDGET_MS);
    phase.put("other_owner_wakeup_ms", Math.max(0, completedAt("other", "worker-other") - enqueued));
    await(() -> "WAITING".equals(jdbc.queryForObject("select work_status from MS_WORK where work_id=?", String.class, recovery))
        && jdbc.queryForObject("select lease_token from MS_WORK where work_id=?", String.class, recovery) == null, 4500);
    phase.put("recovery_wakeup_upper_ms", Math.max(0, System.currentTimeMillis() - expiry));
    Thread.sleep(4000); end(phase);
    @SuppressWarnings("unchecked") var counts = (Map<String, Long>) phase.get("scheduling_delta");
    assertEquals(1L, counts.get("owner_calls")); assertEquals(1L, counts.get("completed_syncs"));
    assertTrue(counts.get("deferred") >= 1); assertTrue(counts.get("quota_denials") >= 1);
    assertEquals(0L, completedAt("cooldown")); assertEquals(0L, completedAt("recovery"));
    assertTrue(((Number) phase.get("recovery_wakeup_upper_ms")).longValue() <= WAKE_BUDGET_MS);
    phase.put("status", "PASS"); WorkerMeasurements.write(report);
    var resumed = begin("cooldown-expires");
    resumed.put("eligible_at_ms", cooldownUntil); worker.start();
    await(() -> completedAt("cooldown") > 0 && completedAt("recovery") > 0,
        Math.max(0, cooldownUntil - System.currentTimeMillis()) + WAKE_BUDGET_MS);
    long observed = System.currentTimeMillis();
    resumed.put("completion_observed_at_ms", observed);
    resumed.put("wakeup_latency_upper_ms", Math.max(0, observed - cooldownUntil)); end(resumed);
    @SuppressWarnings("unchecked") var resumedCounts = (Map<String, Long>) resumed.get("scheduling_delta");
    assertEquals(2L, resumedCounts.get("owner_calls")); assertEquals(2L, resumedCounts.get("completed_syncs"));
    assertTrue(observed - cooldownUntil <= WAKE_BUDGET_MS);
    resumed.put("status", "PASS"); WorkerMeasurements.write(report);
  }

  private Map<String, Object> begin(String name) {
    var phase = new LinkedHashMap<String, Object>(); phase.put("name", name); phase.put("status", "FAILED");
    var properties = context.getBean(ModelSyncProperties.class);
    phase.put("execution_mode", "auto-configured-ReactorSyncWorker-SmartLifecycle");
    phase.put("poll_interval_ms", properties.getPollInterval().toMillis());
    phase.put("productive_interval_ms", properties.getProductiveInterval().toMillis());
    phase.put("maximum_idle_interval_ms", properties.getMaxIdleInterval().toMillis());
    phase.put("scheduling_before", WorkerMeasurements.scheduling(worker, jdbc, owner, quota));
    phase.put("sessions_before", WorkerMeasurements.sessions(jdbc));
    phase.put("session_before", WorkerMeasurements.counters(jdbc)); phase.put("started_ns", System.nanoTime());
    phases.add(phase); return phase;
  }
  @SuppressWarnings("unchecked") private void end(Map<String, Object> phase) {
    await(() -> worker.activity().inFlight() == 0 && owner.active.get() == 0, 3000);
    long elapsed = (System.nanoTime() - ((Number) phase.get("started_ns")).longValue()) / 1000000;
    phase.put("elapsed_ms", elapsed);
    var after = WorkerMeasurements.scheduling(worker, jdbc, owner, quota);
    var nativeAfter = WorkerMeasurements.counters(jdbc);
    var sessionsAfter = WorkerMeasurements.sessions(jdbc);
    phase.put("sessions_after", sessionsAfter);
    phase.put("scheduling_after", after); phase.put("session_after", nativeAfter);
    phase.put("session_attribution", phase.get("sessions_before").equals(sessionsAfter) && sessionsAfter.size() == 8
        ? "VALID" : "UNATTRIBUTABLE");
    assertEquals(8, sessionsAfter.size());
    assertEquals(phase.get("sessions_before"), sessionsAfter, "Native counter interval lost physical session continuity");
    var delta = WorkerMeasurements.delta((Map<String, Long>) phase.get("scheduling_before"), after);
    var nativeDelta = WorkerMeasurements.delta((Map<String, Long>) phase.get("session_before"), nativeAfter);
    phase.put("scheduling_delta", delta); phase.put("session_delta", nativeDelta);
    assertEquals(0L, delta.get("failed"), "Infrastructure/owner failure in a benign pacing probe");
    phase.put("polls_per_second", 1000.0 * delta.get("ticks") / elapsed);
    phase.put("completed_syncs_per_second", 1000.0 * delta.get("completed_syncs") / elapsed);
    phase.put("logical_gets_per_second", 1000.0 * (nativeDelta.get("consistent gets") + nativeDelta.get("db block gets")) / elapsed);
    stop();
  }
  private void enqueue(String id) { gateway.request("worker-main", "synthetic-worker", id, Priority.URGENT, true); }
  private WorkKey key(String id) { return new WorkKey("worker-main", new SubjectKey("synthetic-worker", id)); }
  private long completed() { return jdbc.queryForObject("select count(*) from MS_STATE s join MS_WORK w on w.work_id=s.work_id where w.binding_id='worker-main' and s.outcome='APPLIED'", Long.class); }
  private long completedAt(String id) { return completedAt(id, "worker-main"); }
  private long completedAt(String id, String binding) {
    var values = jdbc.queryForList("select reconciled_at_ms from MS_STATE where work_id=? and outcome='APPLIED'", Long.class,
        new WorkKey(binding, new SubjectKey("synthetic-worker", id)).storageId());
    return values.isEmpty() ? 0 : values.getFirst();
  }
  private void stop() {
    if (worker == null) return;
    // End measurements before cancellation and cancel in a natural idle gap, not in Oracle I/O.
    await(() -> worker.activity().inFlight() == 0 && owner.active.get() == 0, 3000);
    worker.stop(); await(() -> worker.activity().inFlight() == 0 && owner.active.get() == 0, 3000);
  }
  private void clear() {
    stop();
    jdbc.update("delete from MS_STATE where work_id in (select work_id from MS_WORK where binding_id in ('worker-main','worker-other'))");
    jdbc.update("delete from MS_WORK where binding_id in ('worker-main','worker-other')");
    jdbc.update("delete from SAMPLE_CUSTOMER where tenant_id='synthetic-worker'");
    jdbc.update("update MS_QUOTA set next_allowed_ms=0 where quota_group in ('worker-main','worker-other')");
  }
  private void close() {
    if (context == null) return;
    try { if (jdbc != null) { clear(); jdbc.execute("drop table SAMPLE_CUSTOMER purge"); jdbc.update("delete from MS_QUOTA where quota_group in ('worker-main','worker-other')"); } }
    finally { context.close(); context = null; worker = null; jdbc = null; }
  }
  private static void await(BooleanSupplier condition, long timeout) {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() >= deadline) fail("Worker condition did not complete within " + timeout + " ms");
      try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
  }
}
