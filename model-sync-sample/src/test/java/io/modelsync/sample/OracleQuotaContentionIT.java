package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariDataSource;
import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.*;
import io.modelsync.jdbc.JdbcQuotaGate;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;

/** Small real quota transactions across two independent starter applications and Oracle sessions. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OracleQuotaContentionIT {
  private HikariDataSource observer, sourceA, sourceB;
  private JdbcTemplate view;
  private ConfigurableApplicationContext appA, appB;
  private JdbcQuotaGate gateA, gateB;
  private QuotaTransactionProbe probe;
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "modelsync-quota-owned"));
  private final AtomicInteger physical = new AtomicInteger();
  private final List<Map<String, Object>> cases = new ArrayList<>();
  private final List<String> work = new ArrayList<>();
  private Map<String, Object> report, result;
  private boolean unresolved, tableOwned;
  private String guard;

  @BeforeAll void open() throws Exception {
    report = FaultEvidence.report("shared-quota-contention"); report.put("cases", cases);
    report.put("observation_ms", 5000); report.put("scope_limit", "existing quota rows; first-registration race remains unqualified");
    observer = RecoveryConfiguration.source(System.getenv("MODELSYNC_ORACLE_URL"), 2, 10000); view = new JdbcTemplate(observer); view.setQueryTimeout(2);
    assertEquals(0, view.queryForObject("select count(*) from user_tables where table_name='SAMPLE_CUSTOMER'", Integer.class));
    new ResourceDatabasePopulator(new ClassPathResource("sample-oracle.sql")).execute(observer); tableOwned = true;
    try (var c = observer.getConnection()) { report.put("oracle", c.getMetaData().getDatabaseProductVersion()); report.put("driver", c.getMetaData().getDriverVersion()); }
    report.put("jdk", System.getProperty("java.runtime.version")); report.put("hibernate", org.hibernate.Version.getVersionString());
    report.put("runtime_pools", List.of(3, 3)); report.put("observer_pool", 2); report.put("read_ms", 10000); report.put("connect_ms", 1500); report.put("acquire_ms", 1000); write();
  }
  private ConfigurableApplicationContext app(boolean enabled) {
    return new SpringApplicationBuilder(LockPolicyOracleConfiguration.class).web(WebApplicationType.NONE).profiles("lock-policy-oracle")
        .run("--spring.jpa.hibernate.ddl-auto=validate", "--spring.main.banner-mode=off", "--logging.level.root=WARN",
            "--model-sync.enabled=true", "--model-sync.worker-enabled=false", "--model-sync.aop.enabled=false",
            "--model-sync.jdbc.point-lock-policy=ORACLE_NOWAIT", "--model-sync.jpa.lock-policy=ORACLE_NOWAIT",
            "--model-sync.jdbc.quota-lock-policy=" + (enabled ? "ORACLE_NOWAIT" : "DEFAULT"),
            "--model-sync.quotas.policy-owner=100ms", "--model-sync.retry-base=200ms", "--model-sync.retry-max=400ms", "--model-sync.max-attempts=3");
  }
  private void boot(String name, boolean enabled) throws Exception {
    assertFalse(unresolved); assertEquals(0, physical.get());
    result = new LinkedHashMap<>(); result.put("name", name); result.put("status", "FAILED"); cases.add(result); write();
    appA = app(enabled); appB = app(enabled); sourceA = appA.getBean(HikariDataSource.class); sourceB = appB.getBean(HikariDataSource.class);
    assertNotSame(sourceA, sourceB); gateA = appA.getBean(JdbcQuotaGate.class); gateB = appB.getBean(JdbcQuotaGate.class);
    gateB.register("policy-other", Duration.ofMillis(1)); guard = "quota-guard-" + UUID.randomUUID(); gateA.register(guard, Duration.ofMillis(1));
    var manager = appA.getBean(JpaTransactionManager.class); assertSame(sourceA, manager.getDataSource());
    probe = new QuotaTransactionProbe(new JdbcTemplate(sourceA), view); probe.guard = guard;
    manager.setTransactionExecutionListeners(List.of(probe));
    result.put("quota_policy", enabled ? "ORACLE_NOWAIT" : "DEFAULT"); result.put("manager_default_timeout_seconds", manager.getDefaultTimeout());
    result.put("sessions_a", appA.getBean(LockPolicyObservation.class).runtimeSessions); result.put("sessions_b", appB.getBean(LockPolicyObservation.class).runtimeSessions);
  }
  private long next() { return view.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group='policy-owner'", Long.class); }
  private Connection block() throws Exception {
    var c = observer.getConnection(); c.setAutoCommit(false);
    try (var s = c.prepareStatement("select quota_group from MS_QUOTA where quota_group='policy-owner' for update")) { try (var rows = s.executeQuery()) { assertTrue(rows.next()); } }
    result.put("blocker_released_for_proof", false); return c;
  }
  private void release(Connection c) throws Exception {
    result.put("emergency_release", physical.get() != 0); c.rollback(); c.close(); result.put("blocker_release_cleanup_only", true); write();
  }
  private void refusal(Runnable action) throws Exception {
    view.update("update MS_QUOTA set next_allowed_ms=0 where quota_group=?", guard); probe.transactions.clear(); probe.armed = true;
    long start = System.nanoTime();
    var future = executor.submit(() -> { physical.incrementAndGet(); try { return assertThrows(LocalContention.class, action::run); } finally { physical.decrementAndGet(); } });
    LocalContention failure;
    try { failure = future.get(5, TimeUnit.SECONDS); }
    catch (TimeoutException e) { unresolved = true; result.put("unresolved_physical", true); write(); throw e; }
    finally { probe.armed = false; }
    long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    var error = assertInstanceOf(SQLException.class, failure.getCause()); assertEquals(54, error.getErrorCode()); assertNull(error.getCause());
    assertEquals(0, physical.get()); assertEquals(1, probe.transactions.size(), "One transaction, no recursive retry");
    var observed = probe.transactions.getFirst(); observed.put("client_and_transaction_return_ms", ms); observed.put("oracle_code", error.getErrorCode());
    observed.put("error_type", failure.getClass().getName()); observed.put("suppressed_types", Arrays.stream(failure.getSuppressed()).map(t -> t.getClass().getName()).toList());
    assertEquals("ROLLED_BACK", observed.get("completion"));
    var before = (Map<?, ?>) observed.get("server_before_rollback"); var after = (Map<?, ?>) observed.get("server_after_rollback");
    assertEquals(0, ((Number) before.get("LOCK_WAIT")).intValue()); assertEquals(1, ((Number) before.get("TRANSACTION_ACTIVE")).intValue());
    assertEquals(0, ((Number) after.get("LOCK_WAIT")).intValue()); assertEquals(0, ((Number) after.get("TRANSACTION_ACTIVE")).intValue());
    assertTrue(observed.get("server_sql").toString().toLowerCase(Locale.ROOT).endsWith("nowait"));
    assertEquals(0L, view.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group=?", Long.class, guard));
    try (var c = observer.getConnection(); var s = c.prepareStatement("select quota_group from MS_QUOTA where quota_group=? for update nowait")) {
      c.setAutoCommit(false); s.setString(1, guard); try (var rows = s.executeQuery()) { assertTrue(rows.next()); } c.rollback();
    }
    observed.put("guard_rolled_back_and_reacquired", true);
    @SuppressWarnings("unchecked") var attempts = (List<Map<String, Object>>) result.computeIfAbsent("attempts", k -> new ArrayList<Map<String, Object>>());
    attempts.add(observed); healthy(); write();
  }
  private void healthy() {
    assertEquals(0, physical.get());
    for (var app : List.of(appA, appB)) {
      var ds = app.getBean(HikariDataSource.class); assertEquals(0, ds.getHikariPoolMXBean().getActiveConnections());
      for (var id : app.getBean(LockPolicyObservation.class).runtimeSessions) {
        var state = probe.snapshot(id.get("sid"), id.get("serial"));
        assertEquals(0, ((Number) state.get("LOCK_WAIT")).intValue()); assertEquals(0, ((Number) state.get("TRANSACTION_ACTIVE")).intValue());
      }
      assertEquals(1, new JdbcTemplate(ds).queryForObject("select 1 from dual", Integer.class));
    }
    result.put("healthy_pools", true); result.put("physical_remaining", physical.get());
  }
  private SubjectKey enqueue() {
    var key = new SubjectKey("synthetic-quota", UUID.randomUUID().toString()); work.add(CustomerJpaMapping.storageId(key));
    appA.getBean(SyncGateway.class).request("customer", key.tenant(), key.id(), Priority.NORMAL, true); return key;
  }
  private int calls() { return appA.getBean(LockPolicyObservation.class).ownerCalls.get(); }
  private void pass() throws Exception { healthy(); result.put("owner_calls", calls()); result.put("status", "PASS"); write(); }

  @Test void compatibleDefaultStillCommitsSharedAdmissionAndCooldown() throws Exception {
    boot("default-compatible", false); assertTrue(gateA.acquire("policy-owner").granted()); assertTrue(next() > 0);
    long until = System.currentTimeMillis() + 30000; gateB.cooldown("policy-owner", Instant.ofEpochMilli(until));
    gateA.cooldown("policy-owner", Instant.ofEpochMilli(until - 1000)); assertEquals(until, next());
    assertFalse(gateA.acquire("policy-owner").granted()); result.put("cooldown_ms", until); pass();
  }
  @Test void heldAdmissionRefusesRepeatedlyWhileAnotherInstanceAndGroupContinue() throws Exception {
    boot("held-acquire", true); long initial = next(); var blocker = block();
    try { for (int n = 0; n < 3; n++) { refusal(() -> gateA.acquire("policy-owner")); assertEquals(initial, next()); assertTrue(gateB.acquire("policy-other").granted()); } }
    finally { release(blocker); }
    assertTrue(gateB.acquire("policy-owner").granted()); result.put("recovered_committed_admission", next()); pass();
  }
  @Test void heldCooldownIsNotReportedPersistedAndNeverShortensPriorCooldown() throws Exception {
    boot("held-cooldown", true); long prior = System.currentTimeMillis() + 30000; gateB.cooldown("policy-owner", Instant.ofEpochMilli(prior));
    var blocker = block();
    try { for (int n = 0; n < 3; n++) { refusal(() -> gateA.cooldown("policy-owner", Instant.ofEpochMilli(prior + 30000))); assertEquals(prior, next()); assertTrue(gateB.acquire("policy-other").granted()); } }
    finally { release(blocker); }
    gateB.cooldown("policy-owner", Instant.ofEpochMilli(prior + 30000)); gateA.cooldown("policy-owner", Instant.ofEpochMilli(prior));
    assertEquals(prior + 30000, next()); result.put("prior_cooldown_ms", prior); result.put("persisted_after_release_ms", next()); pass();
  }
  @Test void coordinatorNeverCallsOwnerWithoutCommittedAdmissionAndRecovers() throws Exception {
    boot("coordinator-admission-refusal", true); var key = enqueue(); var coordinator = appA.getBean(SyncCoordinator.class);
    var lease = coordinator.claim().orElseThrow(); var blocker = block();
    try {
      refusal(() -> coordinator.prepare(lease)); assertEquals(0, calls());
      long failedAt = System.currentTimeMillis(); coordinator.failed(lease, new LocalContention("quota admission", null));
      var row = view.queryForMap("select work_status,attempts,last_error_code,next_at_ms from MS_WORK where work_id=?", CustomerJpaMapping.storageId(key));
      assertEquals("WAITING", row.get("WORK_STATUS")); assertEquals("LOCAL_CONTENTION", row.get("LAST_ERROR_CODE")); assertEquals(1, ((Number) row.get("ATTEMPTS")).intValue());
      assertTrue(((Number) row.get("NEXT_AT_MS")).longValue() > failedAt); result.put("durable_retry", row);
      assertEquals(0, view.queryForObject("select count(*) from MS_STATE where work_id=?", Integer.class, CustomerJpaMapping.storageId(key)));
      assertTrue(gateB.acquire("policy-other").granted());
    } finally { release(blocker); }
    var completed = new java.util.concurrent.atomic.AtomicBoolean();
    OracleRecoveryIT.await(() -> { if (!completed.get()) completed.set(coordinator.runOne()); return completed.get(); }, 5000);
    assertEquals(1, calls()); assertEquals("IDLE", view.queryForObject("select work_status from MS_WORK where work_id=?", String.class, CustomerJpaMapping.storageId(key)));
    result.put("completed_syncs", 1); result.put("owner_calls_before_release", 0); pass();
  }
  @Test void failedCooldownAfterOwnerReadPropagatesOnceAndKeepsRecoverableLease() throws Exception {
    boot("owner-cooldown-refusal", true); var key = enqueue(); var coordinator = appA.getBean(SyncCoordinator.class);
    var lease = coordinator.claim().orElseThrow(); var prepared = coordinator.prepare(lease).orElseThrow();
    ((BlockingOwner<?>) prepared.binding().owner()).fetch(prepared.request()); assertEquals(1, calls());
    long committed = next(); var rateLimit = new RateLimitedAfter(Duration.ofMillis(1200)); var blocker = block();
    try {
      refusal(() -> coordinator.failed(lease, rateLimit)); assertEquals(committed, next());
      var durable = view.queryForMap("select work_status,lease_token,generation,attempts from MS_WORK where work_id=?", CustomerJpaMapping.storageId(key));
      assertEquals("LEASED", durable.get("WORK_STATUS")); assertEquals(lease.token(), durable.get("LEASE_TOKEN")); assertEquals(0, ((Number) durable.get("ATTEMPTS")).intValue());
      assertEquals(1, calls()); result.put("durable_after_cooldown_failure", durable); result.put("cooldown_persisted", false);
      result.put("owner_response", "synthetic read completed, RateLimitedAfter injected into failure handler"); assertTrue(gateB.acquire("policy-other").granted());
    } finally { release(blocker); }
    coordinator.failed(lease, rateLimit); long cooldown = next(); assertTrue(cooldown > committed);
    assertEquals("DEFERRED", view.queryForObject("select last_error_code from MS_WORK where work_id=?", String.class, CustomerJpaMapping.storageId(key)));
    gateB.cooldown("policy-owner", Instant.ofEpochMilli(committed)); assertEquals(cooldown, next());
    assertFalse(gateA.acquire("policy-owner").granted()); assertEquals(1, calls());
    var completed = new java.util.concurrent.atomic.AtomicBoolean();
    OracleRecoveryIT.await(() -> { if (!completed.get()) completed.set(coordinator.runOne()); return completed.get(); }, 5000);
    assertEquals(2, calls()); assertEquals("IDLE", view.queryForObject("select work_status from MS_WORK where work_id=?", String.class, CustomerJpaMapping.storageId(key)));
    result.put("cooldown_persisted_after_release", true); result.put("repeated_owner_reads", 1); result.put("completed_syncs", 1); pass();
  }
  @Test void existingRegistrationAcrossInstancesDoesNotChangeAdmissionOrSpacing() throws Exception {
    boot("registration-boundary", true); gateA.cooldown("policy-owner", Instant.now().plusSeconds(30)); long before = next();
    gateB.register("policy-owner", Duration.ofMillis(100)); assertEquals(before, next());
    assertThrows(IllegalStateException.class, () -> gateB.register("policy-owner", Duration.ofMillis(101))); assertEquals(before, next());
    result.put("concurrent_first_insert", "UNQUALIFIED; ordinary INSERT retained; no missing-row reservation"); pass();
  }

  @AfterEach void clean() throws Exception {
    boolean cleaned = false;
    try {
      for (var app : new ConfigurableApplicationContext[] {appA, appB}) if (app != null) {
        var ds = app.getBean(HikariDataSource.class); String name = ds.getPoolName(); app.close(); assertTrue(ds.isClosed()); FaultEvidence.awaitNoPoolThreads(name);
      }
      appA = appB = null; assertEquals(0, physical.get(), "No replacement while physical work remains");
      for (String id : work) { view.update("delete from MS_STATE where work_id=?", id); view.update("delete from MS_WORK where work_id=?", id); view.update("delete from SAMPLE_CUSTOMER where storage_key=?", id); }
      work.clear(); view.update("delete from MS_QUOTA where quota_group in ('policy-owner','policy-other',?)", guard); cleaned = true;
    } finally { if (result != null) { result.put("cleanup_verified", cleaned); if (!cleaned) result.put("status", "FAILED"); } write(); }
  }
  @AfterAll void close() throws Exception {
    executor.shutdown(); boolean ended = executor.awaitTermination(12, TimeUnit.SECONDS);
    if (tableOwned) view.execute("drop table SAMPLE_CUSTOMER purge");
    if (observer != null) { observer.close(); FaultEvidence.awaitNoPoolThreads(observer.getPoolName()); }
    report.put("owned_executor_terminated", ended); report.put("physical_remaining", physical.get());
    report.put("owned_pools_closed", sourceA != null && sourceA.isClosed() && sourceB != null && sourceB.isClosed() && observer.isClosed()); report.put("table_removed", tableOwned);
    report.put("status", ended && physical.get() == 0 && cases.size() == 6 && cases.stream().allMatch(c -> "PASS".equals(c.get("status")) && Boolean.TRUE.equals(c.get("cleanup_verified"))) ? "PASS" : "FAILED");
    write(); assertTrue(ended);
  }
  private void write() throws Exception { FaultEvidence.write("oracle-quota-contention.json", report); }
}
