package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariDataSource;
import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;
import io.modelsync.reactor.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.scheduler.Schedulers;

/** Isolated real Oracle/owner transport faults; never run by default Surefire. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OracleIoIT {
  private HikariDataSource source;
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private SyncCoordinator coordinator;
  private RecoveryControl control;
  private Map<String, Object> report;
  private final List<Map<String, Object>> cases = new ArrayList<>();
  private final List<String> ids = new ArrayList<>();
  private boolean tableOwned;
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "modelsync-owned-io-probe"));

  @BeforeAll void open() throws Exception {
    report = FaultEvidence.report("bounded-owned-oracle-transport-and-shutdown"); report.put("cases", cases);
    report.put("acquisition_ms", 1000); report.put("connect_ms", 1500); report.put("read_ms", 2000);
    report.put("statement_seconds", 2); report.put("transaction_seconds", 2); report.put("jpa_lock_ms", 1000);
    report.put("oracle_net_disableOob", true); report.put("observer_read_ms", 10000);
    report.put("runtime_pool_size", 4); report.put("observer_pool_size", 2);
    report.put("worker_concurrency", 1); report.put("worker_persistence_threads", 1); report.put("worker_owner_threads", 1);
    report.put("owner_fetch_ms", 300); report.put("owner_socket_timeout_ms", 250);
    report.put("shutdown_ms", Map.of("cooperative", 1500, "stalled", 300));
    source = RecoveryConfiguration.source(System.getenv("MODELSYNC_ORACLE_URL"), 2, 10000);
    jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(2);
    assertEquals(0, jdbc.queryForObject("select count(*) from user_tables where table_name='SAMPLE_CUSTOMER'", Integer.class));
    new ResourceDatabasePopulator(new ClassPathResource("sample-oracle.sql")).execute(source); tableOwned = true;
    context = RecoveryProcess.open("--model-sync.fetch-timeout=300ms",
        "--modelsync.recovery.directory=" + FaultEvidence.root().resolve("model-sync-sample/target/qm/io-parent"));
    coordinator = context.getBean(SyncCoordinator.class); control = context.getBean(RecoveryControl.class);
  }

  private Map<String, Object> begin(String name) throws Exception {
    Map<String, Object> value = new LinkedHashMap<>(); value.put("name", name); value.put("status", "FAILED"); cases.add(value);
    FaultEvidence.write("oracle-io.json", report); return value;
  }
  private void pass(Map<String, Object> value) throws Exception {
    value.put("status", "PASS"); FaultEvidence.write("oracle-io.json", report);
  }
  private SubjectKey seed() throws Exception {
    SubjectKey key = new SubjectKey("synthetic-io", UUID.randomUUID().toString());
    ids.add(new WorkKey("customer", key).storageId());
    control.ownerOverride = null; control.value = "before";
    context.getBean(SyncGateway.class).request("customer", key.tenant(), key.id(), Priority.URGENT, true);
    assertTrue(coordinator.runOne()); assertEquals("before", name(key)); return key;
  }
  private String id(SubjectKey key) { return new WorkKey("customer", key).storageId(); }
  private String name(SubjectKey key) { return jdbc.queryForObject("select display_name from SAMPLE_CUSTOMER where storage_key=?", String.class, id(key)); }
  private String validator(SubjectKey key) { return jdbc.queryForObject("select owner_validator from MS_STATE where work_id=?", String.class, id(key)); }
  private void request(SubjectKey key) { context.getBean(SyncGateway.class).request("customer", key.tenant(), key.id(), Priority.URGENT, true); }
  private void unchanged(SubjectKey key) { assertEquals("before", name(key)); assertEquals("before", validator(key)); }

  @Test void poolAcquisitionIsBoundedAndRecovers() throws Exception {
    var result = begin("pool-exhaustion");
    try (var one = source.getConnection(); var two = source.getConnection()) {
      long start = System.nanoTime();
      Exception failure = assertThrows(java.sql.SQLException.class, source::getConnection);
      long elapsed = OracleRecoveryIT.millis(start); assertTrue(elapsed >= 900 && elapsed <= 2500);
      result.put("acquisition_ms", elapsed); result.put("exception", failure.getClass().getSimpleName());
      assertEquals(2, source.getHikariPoolMXBean().getActiveConnections());
    }
    assertEquals(1, jdbc.queryForObject("select 1 from dual", Integer.class));
    assertEquals(0, source.getHikariPoolMXBean().getActiveConnections()); result.put("pool_recovered", true); pass(result);
  }

  @Test void realStatementLockWaitCancelsAndRollsBack() throws Exception {
    var result = begin("statement-lock-timeout"); var key = seed();
    try (var blocker = source.getConnection()) {
      blocker.setAutoCommit(false);
      try (var lock = blocker.prepareStatement("update SAMPLE_CUSTOMER set display_name='held' where storage_key=?")) {
        lock.setString(1, id(key)); assertEquals(1, lock.executeUpdate());
      }
      long began = System.nanoTime();
      var effectiveTimeout = new java.util.concurrent.atomic.AtomicInteger(-1);
      Future<Integer> blocked = executor.submit(() -> {
        var local = new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
        return local.execute(status -> {
          try { return jdbc.execute("update SAMPLE_CUSTOMER set display_name='must-rollback' where storage_key=?",
          (org.springframework.jdbc.core.PreparedStatementCallback<Integer>) statement -> {
            assertFalse(statement.getConnection().getAutoCommit(), "The ledger contract uses local transactions, never autocommit DML");
            effectiveTimeout.set(statement.getQueryTimeout()); statement.setString(1, id(key)); return statement.executeUpdate();
          }); }
          finally { status.setRollbackOnly(); }
        });
      });
      ExecutionException error;
      try { error = assertThrows(ExecutionException.class, () -> blocked.get(5, TimeUnit.SECONDS)); }
      finally {
        result.put("elapsed_ms", OracleRecoveryIT.millis(began));
        result.put("effective_statement_timeout_seconds", effectiveTimeout.get());
        result.put("probe_transaction", "explicit local rollback-only JDBC transaction; driver read timeout 10000ms");
        var stacks = new LinkedHashMap<String, Object>();
        Thread.getAllStackTraces().forEach((thread, stack) -> {
          if (thread.getName().equals("modelsync-owned-io-probe") || thread.getName().contains("OracleTimeout"))
            stacks.put(thread.getName(), java.util.Arrays.stream(stack).limit(20).map(StackTraceElement::toString).toList());
        });
        result.put("bounded_test_thread_stacks", stacks); FaultEvidence.write("oracle-io.json", report);
      }
      assertEquals(2, effectiveTimeout.get());
      assertTrue(OracleRecoveryIT.millis(began) <= 5000); assertTrue(blocked.isDone());
      Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
      assertInstanceOf(java.sql.SQLException.class, cause); assertEquals(1013, ((java.sql.SQLException) cause).getErrorCode());
      result.put("elapsed_ms", OracleRecoveryIT.millis(began)); result.put("oracle_error", 1013);
      blocker.rollback();
    }
    unchanged(key); assertEquals(0, source.getHikariPoolMXBean().getActiveConnections()); result.put("physical_statement_ended", true); pass(result);
  }

  @Test void transactionDeadlineRollsBackEarlierWrites() throws Exception {
    var result = begin("transaction-deadline"); var key = seed();
    var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class)); transaction.setTimeout(2);
    var hostJdbc = new JdbcTemplate(context.getBean(HikariDataSource.class));
    long began = System.nanoTime();
    assertThrows(org.springframework.transaction.TransactionTimedOutException.class, () -> transaction.execute(status -> {
      hostJdbc.update("update SAMPLE_CUSTOMER set display_name='uncommitted' where storage_key=?", id(key));
      try { new CountDownLatch(1).await(2100, TimeUnit.MILLISECONDS); }
      catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
      return hostJdbc.queryForObject("select 1 from dual", Integer.class);
    }));
    result.put("elapsed_ms", OracleRecoveryIT.millis(began)); assertTrue(OracleRecoveryIT.millis(began) <= 5000);
    unchanged(key); result.put("rollback_verified", true); pass(result);
  }

  @Test void databaseReadStallEndsPhysicalCallAndPoolRecovers() throws Exception {
    var result = begin("database-read-stall");
    try (var proxy = new OracleFaultProxy(); var pool = RecoveryConfiguration.source(proxy.url(), 1)) {
      try (var connection = pool.getConnection()) {
        try (var warm = connection.createStatement(); var rows = warm.executeQuery("select 1 from dual")) { assertTrue(rows.next()); }
        proxy.arm(false); long began = System.nanoTime();
        Future<Integer> query = executor.submit(() -> { try (var statement = connection.createStatement(); var rows = statement.executeQuery("select 1 from dual")) { rows.next(); return rows.getInt(1); } });
        assertTrue(proxy.heldPacket.await(2, TimeUnit.SECONDS));
        assertThrows(ExecutionException.class, () -> query.get(5, TimeUnit.SECONDS)); assertTrue(query.isDone());
        result.put("elapsed_ms", OracleRecoveryIT.millis(began)); assertTrue(OracleRecoveryIT.millis(began) <= 5000);
      }
      proxy.release(); assertEquals(1, new JdbcTemplate(pool).queryForObject("select 1 from dual", Integer.class));
      assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections()); result.put("pool_recovered", true);
    }
    result.put("proxy_closed", true); result.put("physical_statement_ended", true); pass(result);
  }

  @Test void unavailableDatabaseConnectionDoesNotWaitIndefinitely() throws Exception {
    var result = begin("connection-unavailable");
    long began = System.nanoTime();
    try (var proxy = new OracleFaultProxy()) {
      proxy.rejectConnections();
      try (var pool = RecoveryConfiguration.source(proxy.url(), 1)) {
      assertThrows(java.sql.SQLException.class, pool::getConnection);
      result.put("acquisition_ms", OracleRecoveryIT.millis(began)); assertTrue(OracleRecoveryIT.millis(began) <= 2500);
      }
    }
    result.put("tcp_timeout_scope", "owned loopback listener resets connections; no silent SYN-blackhole proof"); pass(result);
  }

  @Test void stalledLoginCleansUpItsPhysicalConnectionAttempt() throws Exception {
    var result = begin("database-login-stall");
    try (var proxy = new OracleFaultProxy()) {
      proxy.arm(true); long began = System.nanoTime();
      try (var pool = RecoveryConfiguration.source(proxy.url(), 1)) {
        assertThrows(java.sql.SQLException.class, pool::getConnection);
        assertTrue(proxy.heldPacket.await(2, TimeUnit.SECONDS));
        result.put("acquisition_ms", OracleRecoveryIT.millis(began));
      }
      result.put("acquisition_and_pool_close_ms", OracleRecoveryIT.millis(began));
      assertTrue(OracleRecoveryIT.millis(began) <= 5000);
    }
    result.put("proxy_and_pool_closed", true); pass(result);
  }

  @Test void gracefulStopDrainsARealOwnerSocketAndAtomicCompletion() throws Exception { ownerStop(false); }
  @Test void lateOwnerCannotBecomeASuccessOrAFalseDrain() throws Exception { ownerStop(true); }
  @Test void ownerSocketTimeoutEndsThePhysicalClientIndependentlyOfFetchTimeout() throws Exception {
    var result = begin("owner-socket-timeout"); var key = seed();
    try (var endpoint = new StalledOwnerEndpoint(250)) {
      long began = System.nanoTime();
      Future<?> fetch = executor.submit(() -> endpoint.fetch(new ReadRequest(key, "before")));
      assertTrue(endpoint.received.await(2, TimeUnit.SECONDS));
      ExecutionException failure = assertThrows(ExecutionException.class, () -> fetch.get(2, TimeUnit.SECONDS));
      assertInstanceOf(java.net.SocketTimeoutException.class, failure.getCause());
      assertEquals(0, endpoint.activeClients.get()); unchanged(key);
      result.put("elapsed_ms", OracleRecoveryIT.millis(began)); result.put("physical_client_active", endpoint.activeClients.get());
      result.put("physical_owner_reads", endpoint.calls.get()); endpoint.release.countDown();
      assertTrue(endpoint.exited.await(2, TimeUnit.SECONDS));
    }
    result.put("endpoint_closed", true); pass(result);
  }
  private void ownerStop(boolean late) throws Exception {
    var result = begin(late ? "late-owner-and-bounded-stop" : "graceful-owner-drain"); var key = seed();
    var persistence = Schedulers.newBoundedElastic(1, 8, "modelsync-owned-stop-jpa");
    var owners = Schedulers.newBoundedElastic(1, 8, "modelsync-owned-stop-owner");
    var worker = new ReactorSyncWorker(coordinator, persistence, owners, 1, Duration.ofMillis(10),
        Duration.ofMillis(5), Duration.ofMillis(40), Duration.ofMillis(late ? 300 : 1500));
    try (var endpoint = new StalledOwnerEndpoint(5000)) {
      control.ownerOverride = endpoint; request(key); worker.start(); assertTrue(endpoint.received.await(3, TimeUnit.SECONDS));
      if (late) {
        OracleRecoveryIT.await(() -> jdbc.queryForObject("select attempts from MS_WORK where work_id=?", Integer.class, id(key)) >= 1, 3000);
        request(key); jdbc.update("update SAMPLE_CUSTOMER set display_name='local-edit',local_version=local_version+1 where storage_key=?", id(key));
        worker.stop(); assertEquals(WorkerShutdownStatus.TIMED_OUT, worker.shutdown().status());
        assertEquals(1, worker.shutdown().physicalOperations()); assertEquals(1, endpoint.activeClients.get());
        assertTrue(worker.shutdown().elapsedMillis() <= 800); assertThrows(IllegalStateException.class, worker::start);
        result.put("initial_stop", worker.shutdown()); endpoint.release.countDown();
        OracleRecoveryIT.await(() -> worker.physicalOperations() == 0 && endpoint.activeClients.get() == 0, 3000);
        assertEquals("local-edit", name(key)); assertEquals("before", validator(key));
        assertEquals(3, jdbc.queryForObject("select generation from MS_WORK where work_id=?", Integer.class, id(key)));
        worker.stop();
      } else {
        Future<?> stopping = executor.submit(() -> { worker.stop(); });
        OracleRecoveryIT.await(() -> !worker.isRunning(), 1000); endpoint.release.countDown(); stopping.get(2, TimeUnit.SECONDS);
        assertEquals("late", name(key)); assertEquals("late", validator(key));
      }
      assertEquals(WorkerShutdownStatus.DRAINED, worker.shutdown().status()); assertEquals(0, worker.physicalOperations());
      assertEquals(1, endpoint.calls.get()); assertTrue(endpoint.exited.await(2, TimeUnit.SECONDS));
      result.put("final_stop", worker.shutdown()); result.put("physical_owner_reads", endpoint.calls.get());
      result.put("successful_applications", late ? 0 : 1); result.put("client_active_after", endpoint.activeClients.get());
    } finally { worker.stop(); persistence.dispose(); owners.dispose(); control.ownerOverride = null; }
    FaultEvidence.awaitNoPoolThreads("modelsync-owned-stop-jpa");
    FaultEvidence.awaitNoPoolThreads("modelsync-owned-stop-owner");
    assertEquals(0, context.getBean(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections());
    result.put("owned_schedulers_disposed", persistence.isDisposed() && owners.isDisposed());
    result.put("owned_worker_threads_terminated", true); pass(result);
  }

  @Test void boundedStopDoesNotCallALockedJpaOperationDrained() throws Exception {
    var result = begin("locked-jpa-shutdown"); var key = seed();
    var persistence = Schedulers.newBoundedElastic(1, 8, "modelsync-owned-lock-jpa");
    var owners = Schedulers.newBoundedElastic(1, 8, "modelsync-owned-lock-owner");
    var worker = new ReactorSyncWorker(coordinator, persistence, owners, 1, Duration.ofMillis(10),
        Duration.ofMillis(5), Duration.ofMillis(40), Duration.ofMillis(300));
    try (var blocker = source.getConnection()) {
      blocker.setAutoCommit(false);
      try (var lock = blocker.prepareStatement("update SAMPLE_CUSTOMER set display_name='held' where storage_key=?")) { lock.setString(1, id(key)); lock.executeUpdate(); }
      control.value = "after"; request(key); worker.start();
      OracleRecoveryIT.await(() -> jdbc.queryForObject("select count(*) from v$session where username=user and state='WAITING'"
          + " and blocking_session is not null and event='enq: TX - row lock contention'", Integer.class) > 0, 3000);
      worker.stop(); result.put("stop", worker.shutdown()); assertEquals(WorkerShutdownStatus.TIMED_OUT, worker.shutdown().status());
      assertTrue(worker.shutdown().physicalOperations() > 0); assertTrue(worker.shutdown().elapsedMillis() <= 800);
      blocker.rollback();
      OracleRecoveryIT.await(() -> worker.physicalOperations() == 0, 5000); worker.stop();
      assertEquals(WorkerShutdownStatus.DRAINED, worker.shutdown().status());
      // Cancellation may have raced a physical commit; inspect an atomic pair, never infer rollback from CANCEL.
      assertEquals(name(key), validator(key)); result.put("observed_projection", name(key)); result.put("observed_validator", validator(key));
    } finally { worker.stop(); persistence.dispose(); owners.dispose(); }
    FaultEvidence.awaitNoPoolThreads("modelsync-owned-lock-jpa");
    FaultEvidence.awaitNoPoolThreads("modelsync-owned-lock-owner");
    result.put("owned_worker_threads_terminated", true);
    result.put("physical_operations_after", worker.physicalOperations()); pass(result);
  }

  @Test void connectionLossBeforeCommitIsUncertainUntilFreshInspection() throws Exception { uncertainCommit(true); }
  @Test void lostCommitAcknowledgementDoesNotLoseCommittedCompletion() throws Exception { uncertainCommit(false); }
  private void uncertainCommit(boolean holdUp) throws Exception {
    var result = begin(holdUp ? "uncertain-before-commit" : "uncertain-after-commit"); var key = seed();
    try (var proxy = new OracleFaultProxy(); var host = RecoveryProcess.open("--modelsync.test.jdbc-url=" + proxy.url(),
        "--modelsync.recovery.directory=" + FaultEvidence.root().resolve("model-sync-sample/target/qm/io-uncertain"))) {
      var runtime = host.getBean(SyncCoordinator.class); request(key);
      var lease = runtime.claim().orElseThrow(); var prepared = runtime.prepare(lease).orElseThrow();
      var fault = host.getBean(RecoveryControl.class); fault.commitProxy = proxy; fault.holdCommitUp = holdUp;
      long began = System.nanoTime();
      Future<?> finishing = executor.submit(() -> runtime.finish(prepared, FetchResult.found(new Customer("after", "ACTIVE"), "after")));
      assertTrue(proxy.heldPacket.await(3, TimeUnit.SECONDS));
      if (!holdUp) OracleRecoveryIT.await(() -> "after".equals(name(key)) && "after".equals(validator(key)), 3000);
      assertThrows(ExecutionException.class, () -> finishing.get(5, TimeUnit.SECONDS)); assertTrue(finishing.isDone());
      result.put("caller_outcome", "UNCERTAIN"); result.put("elapsed_ms", OracleRecoveryIT.millis(began));
      assertTrue(OracleRecoveryIT.millis(began) <= 5000);
      // Drop the held request/response, never forward a previously withheld COMMIT later.
      fault.commitProxy = null;
    }
    OracleRecoveryIT.await(() -> (holdUp ? "before" : "after").equals(name(key)), 3000);
    assertEquals(name(key), validator(key)); result.put("fresh_projection", name(key)); result.put("fresh_validator", validator(key));
    String outcome = jdbc.queryForObject("select outcome from MS_STATE where work_id=?", String.class, id(key));
    String status = jdbc.queryForObject("select work_status from MS_WORK where work_id=?", String.class, id(key));
    assertEquals("APPLIED", outcome); assertEquals(holdUp ? "LEASED" : "IDLE", status);
    result.put("fresh_outcome", outcome); result.put("fresh_work_status", status);
    result.put("projection_and_success_atomic", true);
    if (holdUp) {
      OracleRecoveryIT.await(() -> jdbc.queryForObject("select lease_until_ms from MS_WORK where work_id=?", Long.class, id(key)) < System.currentTimeMillis(), 7000);
      control.value = "recovered"; assertTrue(coordinator.runOne()); assertEquals("recovered", name(key)); assertEquals(name(key), validator(key));
      result.put("recovery_completed", true);
    } else {
      assertEquals("IDLE", jdbc.queryForObject("select work_status from MS_WORK where work_id=?", String.class, id(key)));
      assertFalse(coordinator.runOne()); result.put("repeat_required", false);
    }
    pass(result);
  }

  @AfterEach void settleOwnWork() {
    if (jdbc != null) for (String id : ids) {
      jdbc.update("delete from MS_STATE where work_id=?", id); jdbc.update("delete from MS_WORK where work_id=?", id);
      jdbc.update("delete from SAMPLE_CUSTOMER where storage_key=?", id);
    }
    ids.clear(); if (control != null) { control.ownerOverride = null; control.value = "before"; }
  }
  @AfterAll void close() throws Exception {
    executor.shutdown(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    if (context != null) {
      String poolName = context.getBean(HikariDataSource.class).getPoolName();
      context.close(); FaultEvidence.awaitNoPoolThreads(poolName);
    }
    if (source != null) {
      if (tableOwned) { jdbc.execute("drop table SAMPLE_CUSTOMER purge"); jdbc.update("delete from MS_QUOTA where quota_group='recovery-owner'"); }
      assertEquals(0, source.getHikariPoolMXBean().getActiveConnections()); source.close();
      FaultEvidence.awaitNoPoolThreads(source.getPoolName());
    }
    if (report != null) {
      report.put("status", cases.size() == 12 && cases.stream().allMatch(c -> "PASS".equals(c.get("status"))) ? "PASS" : "FAILED");
      report.put("owned_executor_terminated", executor.isTerminated()); report.put("owned_pool_closed", source != null && source.isClosed());
      FaultEvidence.write("oracle-io.json", report);
    }
  }
}
