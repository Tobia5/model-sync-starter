package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariDataSource;
import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.*;
import io.modelsync.jdbc.JdbcWorkStore;
import io.modelsync.jpa.JpaLockPolicy;
import io.modelsync.reactor.ReactorSyncWorker;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in production-adapter integration. No prototype query replacement or native DML extension. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OracleLockPolicyIT {
  private HikariDataSource observer, source;
  private JdbcTemplate view, jdbc;
  private ConfigurableApplicationContext context;
  private TransactionTemplate tx;
  private SyncCoordinator coordinator;
  private SyncGateway gateway;
  private JdbcWorkStore store;
  private QuotaGate gate;
  private LockPolicyObservation probe;
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "modelsync-lock-policy-owned"));
  private final AtomicInteger physical = new AtomicInteger();
  private final List<String> owned = new ArrayList<>();
  private final List<Map<String, Object>> cases = new ArrayList<>();
  private Map<String, Object> report, result;
  private boolean tableOwned, unresolved;

  @BeforeAll void open() throws Exception {
    report = FaultEvidence.report("production-oracle-point-lock-integration");
    report.put("cases", cases); report.put("observation_ms", 5000);
    report.put("policy_scope", "existing-row point lookup/refresh only; no INSERT/UPDATE rewriting");
    observer = RecoveryConfiguration.source(System.getenv("MODELSYNC_ORACLE_URL"), 2, 10000);
    view = new JdbcTemplate(observer); view.setQueryTimeout(2);
    assertEquals(0, view.queryForObject("select count(*) from MS_QUOTA where quota_group='policy-owner'", Integer.class));
    assertEquals(0, view.queryForObject("select count(*) from user_tables where table_name='SAMPLE_CUSTOMER'", Integer.class));
    new ResourceDatabasePopulator(new ClassPathResource("sample-oracle.sql")).execute(observer); tableOwned = true;
    try (var c = observer.getConnection()) {
      report.put("oracle", c.getMetaData().getDatabaseProductVersion()); report.put("driver", c.getMetaData().getDriverVersion());
    }
    report.put("hibernate", org.hibernate.Version.getVersionString()); report.put("jdk", System.getProperty("java.runtime.version"));
    report.put("max_heap_bytes", Runtime.getRuntime().maxMemory()); write();
  }
  @BeforeEach void boundary() { assertFalse(unresolved, "Unresolved physical work forbids replacement probes"); assertEquals(0, physical.get()); }

  private void boot(String name, boolean enabled, boolean worker) throws Exception {
    result = new LinkedHashMap<>(); result.put("name", name); result.put("status", "FAILED"); cases.add(result); write();
    String policy = enabled ? "ORACLE_NOWAIT" : "DEFAULT";
    result.put("jdbc_policy", policy); result.put("jpa_policy", policy);
    result.put("execution_mode", worker ? "actual auto-configured Reactor worker" : "public headless coordinator/gateway");
    context = new SpringApplicationBuilder(LockPolicyOracleConfiguration.class).web(WebApplicationType.NONE)
        .profiles("lock-policy-oracle").run("--spring.jpa.hibernate.ddl-auto=validate", "--spring.main.banner-mode=off",
            "--logging.level.root=WARN", "--model-sync.enabled=true", "--model-sync.aop.enabled=false",
            "--model-sync.worker-enabled=" + worker, "--model-sync.jdbc.point-lock-policy=" + policy,
            "--model-sync.jpa.lock-policy=" + policy, "--model-sync.quotas.policy-owner=1ms",
            "--model-sync.retry-base=200ms", "--model-sync.retry-max=400ms", "--model-sync.max-attempts=3",
            "--model-sync.concurrency=1", "--model-sync.persistence-threads=1", "--model-sync.owner-threads=1",
            "--model-sync.queued-tasks-per-thread=8", "--model-sync.shutdown-timeout=2s");
    source = context.getBean(HikariDataSource.class); probe = context.getBean(LockPolicyObservation.class);
    var manager = context.getBean(JpaTransactionManager.class); assertSame(source, manager.getDataSource());
    assertEquals(enabled ? JpaLockPolicy.ORACLE_NOWAIT : JpaLockPolicy.DEFAULT, context.getBean(JpaLockPolicy.class));
    tx = new TransactionTemplate(manager); tx.setTimeout(2);
    jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(2);
    store = context.getBean(JdbcWorkStore.class); coordinator = context.getBean(SyncCoordinator.class);
    gateway = context.getBean(SyncGateway.class); gate = context.getBean(QuotaGate.class);
    result.put("runtime_sessions", probe.runtimeSessions); result.put("runtime_pool", 3); result.put("observer_pool", 2);
    result.put("manual_attempt_transaction_seconds", 2); result.put("manager_default_timeout_seconds", manager.getDefaultTimeout());
    result.put("socket_read_ms", 10000); result.put("connect_ms", 1500); result.put("acquire_ms", 1000);
    result.put("sql_capture", "read-only Hibernate StatementInspector returns identical SQL"); write();
  }
  private SubjectKey key() { var key = new SubjectKey("synthetic-policy", UUID.randomUUID().toString()); owned.add(CustomerJpaMapping.storageId(key)); return key; }
  private void seed(SubjectKey key) {
    view.update("insert into SAMPLE_CUSTOMER(storage_key,tenant_id,source_id,local_version,display_name,customer_status) values(?,?,?,0,'before','ACTIVE')",
        CustomerJpaMapping.storageId(key), key.tenant(), key.id());
  }
  private void enqueue(SubjectKey key) { gateway.request("customer", key.tenant(), key.id(), Priority.NORMAL, true); }
  private PreparedSync<?> prepare(SubjectKey key) throws Exception {
    enqueue(key); var lease = coordinator.claim().orElseThrow(); assertEquals(key, lease.key().subject());
    var prepared = coordinator.prepare(lease).orElseThrow();
    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    ((BlockingOwner<?>) prepared.binding().owner()).fetch(prepared.request()); return prepared;
  }
  private WorkLease guard() {
    SubjectKey key = key(); var work = new WorkKey("customer", key);
    tx.executeWithoutResult(s -> store.enqueue(work, Priority.URGENT, true, Instant.now()));
    return tx.execute(s -> store.claim(Priority.URGENT, Instant.now(), Duration.ofSeconds(60)).orElseThrow());
  }
  private Connection block(String table, String column, SubjectKey key) throws Exception {
    assertTrue(Set.of("MS_WORK", "SAMPLE_CUSTOMER").contains(table));
    var blocker = observer.getConnection(); blocker.setAutoCommit(false);
    try (var statement = blocker.prepareStatement("select " + column + " from " + table + " where " + column + "=? for update")) {
      statement.setString(1, CustomerJpaMapping.storageId(key)); try (var rows = statement.executeQuery()) { assertTrue(rows.next()); }
    }
    result.put("blocker_released_for_proof", false); return blocker;
  }
  private void release(Connection blocker) throws Exception {
    result.put("physical_before_cleanup", physical.get()); result.put("emergency_release", physical.get() != 0);
    blocker.rollback(); blocker.close(); result.put("blocker_release_cleanup_only", true); write();
  }
  private Map<String, Object> session(int sid, int serial) {
    var values = new LinkedHashMap<>(view.queryForMap("select status,event,state,sql_id,prev_sql_id,"
        + "case when taddr is null then 0 else 1 end TRANSACTION_ACTIVE,"
        + "case when event='enq: TX - row lock contention' and state='WAITING' then 1 else 0 end LOCK_WAIT"
        + " from v$session where sid=? and serial#=?", sid, serial));
    values.put("SID", sid); values.put("SERIAL", serial); return values;
  }
  private List<Map<String, Object>> poolSessions() { return probe.runtimeSessions.stream().map(s -> session(s.get("sid"), s.get("serial"))).toList(); }
  private void healthy() throws Exception {
    assertEquals(0, physical.get()); assertEquals(0, source.getHikariPoolMXBean().getActiveConnections());
    var states = poolSessions();
    for (var state : states) { assertEquals(0, ((Number) state.get("LOCK_WAIT")).intValue()); assertEquals(0, ((Number) state.get("TRANSACTION_ACTIVE")).intValue()); }
    assertEquals(1, jdbc.queryForObject("select 1 from dual", Integer.class));
    result.put("pool_sessions_after", states); result.put("pool_active_after", source.getHikariPoolMXBean().getActiveConnections());
    result.put("healthy_subsequent_query", true);
  }
  private LockPolicyAttempt attempt(Runnable operation, WorkLease guard) throws Exception {
    var measurements = new LinkedHashMap<String, Object>(); probe.sql.clear();
    Future<LockPolicyAttempt> future = executor.submit(() -> {
      long start = System.nanoTime(); int[] identity = new int[2]; var receipt = new NowaitTransactionReceipt(); Throwable failure = null;
      physical.incrementAndGet();
      try {
        tx.executeWithoutResult(status -> {
          var id = jdbc.queryForMap("select sid,serial# from v$session where sid=to_number(sys_context('USERENV','SID'))");
          identity[0] = ((Number) id.get("SID")).intValue(); identity[1] = ((Number) id.get("SERIAL#")).intValue();
          TransactionSynchronizationManager.registerSynchronization(receipt);
          if (guard != null) { assertTrue(store.lockValid(guard, Instant.now())); jdbc.update("update MS_WORK set last_error_code='attempt-marker' where work_id=?", guard.key().storageId()); }
          try { operation.run(); }
          catch (RuntimeException refused) {
            measurements.put("client_refusal_ms", elapsed(start)); measurements.put("server_before_rollback", session(identity[0], identity[1]));
            Object sqlId = ((Map<?, ?>) measurements.get("server_before_rollback")).get("PREV_SQL_ID");
            measurements.put("server_sql", view.queryForObject("select sql_fulltext from v$sql where sql_id=? and rownum=1", String.class, sqlId));
            throw refused;
          }
        });
      } catch (Exception error) { failure = error; measurements.put("error_chain", errors(error)); }
      finally { physical.decrementAndGet(); }
      measurements.put("transaction_end_ms", elapsed(start)); measurements.put("transaction_completion", receipt.status);
      measurements.put("server_after_transaction", session(identity[0], identity[1])); measurements.put("jpa_sql", List.copyOf(probe.sql));
      return new LockPolicyAttempt(measurements, failure, identity[0], identity[1]);
    });
    try {
      var attempt = future.get(5, TimeUnit.SECONDS); result.put("attempt", attempt.evidence()); write(); return attempt;
    } catch (TimeoutException timeout) { unresolved = true; result.put("unresolved_physical", true); write(); throw timeout; }
  }
  private void refused(LockPolicyAttempt attempt, WorkLease guard) throws Exception {
    assertInstanceOf(LocalContention.class, attempt.failure()); assertTrue(oracle54(attempt.failure()));
    assertEquals(1, attempt.evidence().get("transaction_completion"));
    var before = (Map<?, ?>) attempt.evidence().get("server_before_rollback");
    var after = (Map<?, ?>) attempt.evidence().get("server_after_transaction");
    assertEquals(0, ((Number) before.get("LOCK_WAIT")).intValue()); assertEquals(1, ((Number) before.get("TRANSACTION_ACTIVE")).intValue());
    assertEquals(0, ((Number) after.get("LOCK_WAIT")).intValue()); assertEquals(0, ((Number) after.get("TRANSACTION_ACTIVE")).intValue());
    assertTrue(attempt.evidence().get("server_sql").toString().toLowerCase(Locale.ROOT).endsWith("nowait"));
    if (guard != null) {
      assertNull(view.queryForObject("select last_error_code from MS_WORK where work_id=?", String.class, guard.key().storageId()));
      try (var c = observer.getConnection(); var s = c.prepareStatement("select work_id from MS_WORK where work_id=? for update nowait")) {
        c.setAutoCommit(false); s.setString(1, guard.key().storageId()); try (var rows = s.executeQuery()) { assertTrue(rows.next()); } c.rollback();
      }
      result.put("guard_write_rolled_back_and_lock_released", true);
    }
    healthy();
  }
  private void unchanged(SubjectKey key) {
    assertEquals("before", view.queryForObject("select display_name from SAMPLE_CUSTOMER where storage_key=?", String.class, CustomerJpaMapping.storageId(key)));
    assertEquals(0L, view.queryForObject("select local_version from SAMPLE_CUSTOMER where storage_key=?", Long.class, CustomerJpaMapping.storageId(key)));
    assertEquals(0, view.queryForObject("select count(*) from MS_STATE where work_id=?", Integer.class, CustomerJpaMapping.storageId(key)));
    result.put("fresh_projection_before_and_no_success_state", true);
  }
  private void pass() throws Exception { result.put("owner_calls", probe.ownerCalls.get()); result.put("jpa_sql", List.copyOf(probe.sql)); result.put("status", "PASS"); write(); }

  @Test void disabledPolicyKeepsCompatibleSqlAndSuccess() throws Exception {
    boot("disabled-compatible", false, false); var key = key(); seed(key); var prepared = prepare(key);
    tx.executeWithoutResult(s -> coordinator.finish(prepared, FetchResult.found(new Customer("after", "ACTIVE"), "after")));
    assertTrue(probe.sql.stream().anyMatch(s -> s.toLowerCase(Locale.ROOT).contains("for update")));
    assertTrue(probe.sql.stream().noneMatch(s -> s.toLowerCase(Locale.ROOT).contains("nowait")));
    assertEquals("IDLE", view.queryForObject("select work_status from MS_WORK where work_id=?", String.class, prepared.lease().key().storageId()));
    healthy(); pass();
  }
  @Test void enabledProducerRefusalIsNeverAccepted() throws Exception {
    boot("producer-refusal", true, false); var key = key(); enqueue(key); var guard = guard();
    var blocker = block("MS_WORK", "work_id", key);
    try {
      var attempt = attempt(() -> enqueue(key), guard); refused(attempt, guard);
      assertEquals(1L, view.queryForObject("select generation from MS_WORK where work_id=?", Long.class, CustomerJpaMapping.storageId(key)));
      assertEquals("WAITING", view.queryForObject("select work_status from MS_WORK where work_id=?", String.class, CustomerJpaMapping.storageId(key)));
      assertEquals(0, probe.ownerCalls.get()); result.put("enqueue_accepted", false);
    } finally { release(blocker); }
    enqueue(key); assertEquals(2L, view.queryForObject("select generation from MS_WORK where work_id=?", Long.class, CustomerJpaMapping.storageId(key))); pass();
  }
  @Test void resultRefusalRollsBackBeforeDurableRetryAndRespectsCooldown() throws Exception {
    boot("result-refusal-recovery", true, false); var key = key(); seed(key); var prepared = prepare(key); var guard = guard();
    Instant cooldown = Instant.now().plusMillis(1500); gate.cooldown("policy-owner", cooldown);
    long admission = view.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group='policy-owner'", Long.class);
    var blocker = block("SAMPLE_CUSTOMER", "storage_key", key);
    try {
      var attempt = attempt(() -> coordinator.finish(prepared, FetchResult.found(new Customer("attempt", "ACTIVE"), "attempt")), guard);
      refused(attempt, guard); unchanged(key);
      assertTrue(probe.sql.stream().noneMatch(s -> s.toLowerCase(Locale.ROOT).startsWith("update sample_customer")));
      assertEquals(prepared.lease().token(), view.queryForObject("select lease_token from MS_WORK where work_id=?", String.class, prepared.lease().key().storageId()));
      coordinator.failed(prepared.lease(), attempt.failure());
      var retry = view.queryForMap("select work_status,attempts,last_error_code,next_at_ms,generation from MS_WORK where work_id=?", prepared.lease().key().storageId());
      assertEquals("WAITING", retry.get("WORK_STATUS")); assertEquals("LOCAL_CONTENTION", retry.get("LAST_ERROR_CODE")); assertEquals(1, ((Number) retry.get("ATTEMPTS")).intValue());
      assertEquals(admission, view.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group='policy-owner'", Long.class));
      assertFalse(coordinator.runOne()); assertEquals(1, probe.ownerCalls.get());
      result.put("retry", retry); result.put("owner_calls_before_recovery", 1); result.put("cooldown_unchanged_ms", admission); healthy();
    } finally { release(blocker); }
    // Remove only the guard control, not target intent; later admission/retry deadlines expire naturally.
    view.update("delete from MS_WORK where work_id=?", guard.key().storageId());
    var ran = new AtomicBoolean();
    OracleRecoveryIT.await(() -> { if (!ran.get()) ran.set(coordinator.runOne()); return ran.get(); }, 5000);
    assertEquals(2, probe.ownerCalls.get()); assertEquals("after", view.queryForObject("select display_name from SAMPLE_CUSTOMER where storage_key=?", String.class, prepared.lease().key().storageId()));
    assertThrows(LeaseLost.class, () -> coordinator.finish(prepared, FetchResult.found(new Customer("obsolete", "ACTIVE"), "obsolete")));
    result.put("later_completed_syncs", 1); result.put("repeated_owner_reads", 1); healthy(); pass();
  }
  @Test void lockedRetryMetadataIsReportedOnceAndLeavesLeaseRecoverable() throws Exception {
    boot("retry-metadata-refusal", true, false); var key = key(); seed(key); var prepared = prepare(key); var guard = guard();
    long admission = view.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group='policy-owner'", Long.class);
    var blocker = block("MS_WORK", "work_id", key);
    try {
      var attempt = attempt(() -> coordinator.finish(prepared, FetchResult.found(new Customer("attempt", "ACTIVE"), "attempt")), guard);
      refused(attempt, guard); unchanged(key);
      long before = System.nanoTime(); var failure = assertThrows(LocalContention.class, () -> coordinator.failed(prepared.lease(), attempt.failure()));
      result.put("retry_metadata_call_ms", elapsed(before)); result.put("retry_metadata_error_chain", errors(failure));
      assertTrue(oracle54(failure)); assertArrayEquals(new Throwable[] {attempt.failure()}, failure.getSuppressed());
      var durable = view.queryForMap("select work_status,lease_token,generation,attempts from MS_WORK where work_id=?", prepared.lease().key().storageId());
      assertEquals("LEASED", durable.get("WORK_STATUS")); assertEquals(prepared.lease().token(), durable.get("LEASE_TOKEN"));
      assertEquals(prepared.lease().generation(), ((Number) durable.get("GENERATION")).longValue()); assertEquals(0, ((Number) durable.get("ATTEMPTS")).intValue());
      assertEquals(admission, view.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group='policy-owner'", Long.class));
      assertEquals(1, probe.ownerCalls.get()); result.put("durable_after_metadata_refusal", durable); healthy();
    } finally { release(blocker); }
    view.update("delete from MS_WORK where work_id=?", guard.key().storageId());
    view.update("update MS_WORK set lease_until_ms=0,next_at_ms=0 where work_id=?", prepared.lease().key().storageId());
    assertTrue(coordinator.runOne()); assertEquals(2, probe.ownerCalls.get());
    assertEquals("IDLE", view.queryForObject("select work_status from MS_WORK where work_id=?", String.class, prepared.lease().key().storageId()));
    result.put("recovery_fixture_expiry_after_cleanup", true); result.put("later_completed_syncs", 1); healthy(); pass();
  }
  @Test void firstInsertsRemainOrdinaryAndUncontendedFunctionalityWorks() throws Exception {
    boot("uncontended-first-inserts", true, false); var key = key(); enqueue(key); assertTrue(coordinator.runOne());
    assertEquals("after", view.queryForObject("select display_name from SAMPLE_CUSTOMER where storage_key=?", String.class, CustomerJpaMapping.storageId(key)));
    assertEquals("IDLE", view.queryForObject("select work_status from MS_WORK where work_id=?", String.class, CustomerJpaMapping.storageId(key)));
    assertEquals(1, view.queryForObject("select count(*) from MS_STATE where work_id=?", Integer.class, CustomerJpaMapping.storageId(key)));
    assertTrue(probe.sql.stream().anyMatch(s -> s.toLowerCase(Locale.ROOT).startsWith("insert into sample_customer") && !s.toLowerCase(Locale.ROOT).contains("nowait")));
    result.put("insert_conflict_bound", "UNQUALIFIED; no DML NOWAIT capability shipped"); healthy(); pass();
  }
  @Test void enabledSuccessRetainsRefreshGenerationFencingAndLocalRevision() throws Exception {
    boot("enabled-safety-controls", true, false); var key = key(); seed(key); var prepared = prepare(key);
    gateway.request("customer", key.tenant(), key.id(), Priority.URGENT, true); probe.sql.clear();
    probe.beforeProjectionUpdate = () -> {
      try (var c = observer.getConnection(); var s = c.prepareStatement("select storage_key from SAMPLE_CUSTOMER where storage_key=? for update nowait")) {
        c.setAutoCommit(false); s.setString(1, prepared.lease().key().storageId());
        var refusal = assertThrows(SQLException.class, s::executeQuery);
        assertEquals(54, refusal.getErrorCode(), "The lookup lock must remain held through the actual refresh");
        c.rollback(); result.put("refresh_lock_retained_before_flush", true);
      } catch (SQLException error) { throw new IllegalStateException("Refresh lock observation failed", error); }
    };
    try { coordinator.finish(prepared, FetchResult.found(new Customer("after", "ACTIVE"), "after")); }
    finally { probe.beforeProjectionUpdate = null; }
    result.put("successful_lookup_refresh_sql", List.copyOf(probe.sql));
    var locking = probe.sql.stream().filter(s -> s.toLowerCase(Locale.ROOT).contains("for update")).toList();
    assertEquals(1, locking.size(), "Pinned Hibernate elides a redundant refresh lock when an exclusive lookup lock is held");
    assertTrue(locking.getFirst().toLowerCase(Locale.ROOT).endsWith("nowait"));
    assertTrue(probe.sql.stream().anyMatch(s -> s.startsWith("select ") && s.contains("display_name") && !s.contains("for update")));
    assertEquals(true, result.get("refresh_lock_retained_before_flush"));
    assertEquals("WAITING", view.queryForObject("select work_status from MS_WORK where work_id=?", String.class, prepared.lease().key().storageId()));
    assertThrows(LeaseLost.class, () -> coordinator.finish(prepared, FetchResult.found(new Customer("obsolete", "ACTIVE"), "obsolete")));
    var newer = coordinator.prepare(coordinator.claim().orElseThrow()).orElseThrow();
    view.update("update SAMPLE_CUSTOMER set display_name='local-change',local_version=local_version+1 where storage_key=?", prepared.lease().key().storageId());
    coordinator.finish(newer, FetchResult.found(new Customer("must-not-apply", "ACTIVE"), "wrong"));
    assertEquals("local-change", view.queryForObject("select display_name from SAMPLE_CUSTOMER where storage_key=?", String.class, prepared.lease().key().storageId()));
    assertEquals("LOCAL_CONFLICT", view.queryForObject("select last_error_code from MS_WORK where work_id=?", String.class, prepared.lease().key().storageId()));
    result.put("generation_fencing_revision_preserved", true); healthy(); pass();
  }
  @Test void actualWorkerBoundsContentionRetriesWithoutForgettingOwnerReads() throws Exception {
    boot("actual-worker-contention-exhaustion", true, true); var key = key(); seed(key);
    var blocker = block("SAMPLE_CUSTOMER", "storage_key", key);
    try {
      enqueue(key);
      OracleRecoveryIT.await(() -> "DEAD".equals(view.queryForObject("select work_status from MS_WORK where work_id=?", String.class, CustomerJpaMapping.storageId(key))), 5000);
      var worker = context.getBean(ReactorSyncWorker.class); worker.stop();
      assertEquals(io.modelsync.reactor.WorkerShutdownStatus.DRAINED, worker.shutdown().status()); assertEquals(0, worker.physicalOperations());
      assertEquals(3, probe.ownerCalls.get()); assertEquals(3L, worker.activity().failed()); assertEquals(0L, worker.activity().fetched());
      var durable = view.queryForMap("select work_status,attempts,last_error_code,generation from MS_WORK where work_id=?", CustomerJpaMapping.storageId(key));
      assertEquals("LOCAL_CONTENTION", durable.get("LAST_ERROR_CODE")); assertEquals(3, ((Number) durable.get("ATTEMPTS")).intValue());
      unchanged(key); healthy(); result.put("durable", durable); result.put("worker_activity", worker.activity()); result.put("worker_shutdown", worker.shutdown());
      result.put("worker_physical_after_stop", worker.physicalOperations()); result.put("completed_syncs", 0);
    } finally { release(blocker); }
    assertFalse(coordinator.runOne(), "DEAD work is not automatically revived"); pass();
  }

  @AfterEach void cleanupCase() throws Exception {
    boolean cleaned = false;
    try {
    if (context != null) {
      String pool = source.getPoolName(); context.close(); assertTrue(source.isClosed()); FaultEvidence.awaitNoPoolThreads(pool);
      FaultEvidence.awaitNoPoolThreads("model-sync-jpa"); FaultEvidence.awaitNoPoolThreads("model-sync-owner"); context = null;
    }
    if (physical.get() != 0) { unresolved = true; write(); throw new AssertionError("Physical operation remains; no replacement probe"); }
    for (String id : owned) { view.update("delete from MS_STATE where work_id=?", id); view.update("delete from MS_WORK where work_id=?", id); view.update("delete from SAMPLE_CUSTOMER where storage_key=?", id); }
    owned.clear(); view.update("delete from MS_QUOTA where quota_group='policy-owner'"); cleaned = true;
    } finally {
      if (result != null) { result.put("cleanup_verified", cleaned); if (!cleaned) result.put("status", "FAILED"); }
      write();
    }
  }
  @AfterAll void close() throws Exception {
    executor.shutdown(); boolean ended = executor.awaitTermination(12, TimeUnit.SECONDS);
    if (tableOwned) { view.execute("drop table SAMPLE_CUSTOMER purge"); assertEquals(0, view.queryForObject("select count(*) from user_tables where table_name='SAMPLE_CUSTOMER'", Integer.class)); }
    if (observer != null) { observer.close(); FaultEvidence.awaitNoPoolThreads(observer.getPoolName()); }
    report.put("owned_executor_terminated", ended); report.put("physical_remaining", physical.get());
    report.put("owned_pools_closed", (source == null || source.isClosed()) && observer != null && observer.isClosed()); report.put("table_removed", tableOwned);
    report.put("status", ended && physical.get() == 0 && cases.size() == 7 && cases.stream().allMatch(c -> "PASS".equals(c.get("status")) && Boolean.TRUE.equals(c.get("cleanup_verified"))) ? "PASS" : "FAILED");
    write(); assertTrue(ended);
  }
  private void write() throws Exception { FaultEvidence.write("oracle-lock-policy.json", report); }
  private static boolean oracle54(Throwable error) {
    for (Throwable c = error; c != null; c = c.getCause()) if (c instanceof SQLException sql && sql.getErrorCode() == 54) return true;
    return false;
  }
  private static List<Map<String, Object>> errors(Throwable error) {
    var errors = new ArrayList<Map<String, Object>>();
    for (Throwable c = error; c != null && errors.size() < 10; c = c.getCause()) {
      var value = new LinkedHashMap<String, Object>(); value.put("type", c.getClass().getName());
      if (c instanceof SQLException sql) value.put("oracle_code", sql.getErrorCode()); errors.add(value);
    }
    return errors;
  }
  private static long elapsed(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start); }
}
