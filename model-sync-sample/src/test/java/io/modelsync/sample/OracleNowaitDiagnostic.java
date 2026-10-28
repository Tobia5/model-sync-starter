package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariDataSource;
import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;
import io.modelsync.jdbc.JdbcWorkStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Small opt-in database-refusal prototype; no old cancellation or full suite is selected. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OracleNowaitDiagnostic {
  private HikariDataSource observer;
  private HikariDataSource source;
  private JdbcTemplate view;
  private NowaitPointJdbcTemplate jdbc;
  private ConfigurableApplicationContext context;
  private TransactionTemplate tx;
  private EntityManager em;
  private JdbcWorkStore store;
  private SyncCoordinator coordinator;
  private SyncGateway gateway;
  private NowaitSqlProbe trace;
  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "modelsync-nowait-owned"));
  private final AtomicInteger physical = new AtomicInteger();
  private final List<String> owned = new ArrayList<>();
  private final List<Map<String, Object>> cases = new ArrayList<>();
  private Map<String, Object> report;
  private boolean tableOwned;
  private boolean unresolved;

  @BeforeAll void open() throws Exception {
    report = FaultEvidence.report("database-NOWAIT-prototype-DIAGNOSTIC_ONLY");
    report.put("cases", cases); report.put("full_qualification", "NOT_RUN");
    report.put("production_policy", "UNCHANGED; test-only exact SQL prototype");
    report.put("attempts_per_blocker", 3); report.put("observation_ms", 5000);
    report.put("jdbc_query_seconds", 2); report.put("socket_read_ms", 10000);
    observer = RecoveryConfiguration.source(System.getenv("MODELSYNC_ORACLE_URL"), 2, 10000);
    view = new JdbcTemplate(observer); view.setQueryTimeout(2);
    assertEquals(0, view.queryForObject("select count(*) from user_tables where table_name='SAMPLE_CUSTOMER'", Integer.class));
    new ResourceDatabasePopulator(new ClassPathResource("sample-oracle.sql")).execute(observer); tableOwned = true;
    context = new SpringApplicationBuilder(NowaitOracleConfiguration.class).web(WebApplicationType.NONE)
        .profiles("nowait-oracle").run("--spring.jpa.hibernate.ddl-auto=validate", "--spring.main.banner-mode=off",
            "--logging.level.root=WARN", "--model-sync.enabled=true", "--model-sync.worker-enabled=false",
            "--model-sync.aop.enabled=false", "--model-sync.lease=60s", "--model-sync.retry-base=100ms",
            "--model-sync.quotas.nowait-owner=1ms");
    source = context.getBean(HikariDataSource.class); jdbc = context.getBean(NowaitPointJdbcTemplate.class);
    var manager = context.getBean(JpaTransactionManager.class); assertSame(source, manager.getDataSource());
    tx = new TransactionTemplate(manager); store = context.getBean(JdbcWorkStore.class);
    coordinator = context.getBean(SyncCoordinator.class); gateway = context.getBean(SyncGateway.class);
    trace = context.getBean(NowaitSqlProbe.class);
    em = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));
    report.put("hibernate", org.hibernate.Version.getVersionString()); report.put("jdk", System.getProperty("java.runtime.version"));
    try (var c = observer.getConnection()) { report.put("oracle", c.getMetaData().getDatabaseProductVersion()); report.put("driver", c.getMetaData().getDriverVersion()); }
    report.put("max_heap_bytes", Runtime.getRuntime().maxMemory()); report.put("runtime_pool", 3); report.put("observer_pool", 2);
    write();
  }

  @BeforeEach void reset() { assertFalse(unresolved, "Prior uncertain attempt forbids further probes"); assertEquals(0, physical.get(), "Prior physical operation unresolved"); trace.reset(); }
  @Test void jdbcPointLocksRefuseWithoutProjectionWork() throws Exception { sequence("jdbc-point"); }
  @Test void jpaLookupRefusesBeforeRefreshOrDml() throws Exception { sequence("jpa-lookup"); }
  @Test void actualJpaRefreshUsesSessionNowait() throws Exception { sequence("jpa-refresh"); }
  @Test void flushExtraRowUsesExplicitDmlNowait() throws Exception { sequence("flush-extra-row"); }
  @Test void racingUniqueInsertUsesExplicitDmlNowait() throws Exception { sequence("insert-race"); }

  private SubjectKey key() {
    var key = new SubjectKey("synthetic-nowait", UUID.randomUUID().toString()); owned.add(CustomerJpaMapping.storageId(key)); return key;
  }
  private void insert(Connection c, SubjectKey key) throws SQLException {
    try (var s = c.prepareStatement("insert into SAMPLE_CUSTOMER(storage_key,tenant_id,source_id,local_version,display_name,customer_status) values(?,?,?,0,'before','ACTIVE')")) {
      s.setString(1, CustomerJpaMapping.storageId(key)); s.setString(2, key.tenant()); s.setString(3, key.id()); assertEquals(1, s.executeUpdate());
    }
  }
  private PreparedSync<?> prepare(SubjectKey key, boolean existing) throws Exception {
    if (existing) try (var c = observer.getConnection()) { insert(c, key); }
    gateway.request("customer", key.tenant(), key.id(), Priority.NORMAL, true);
    var lease = coordinator.claim().orElseThrow(); assertEquals(key, lease.key().subject());
    var p = coordinator.prepare(lease).orElseThrow();
    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    ((io.modelsync.core.port.BlockingOwner<?>) p.binding().owner()).fetch(p.request());
    return p;
  }
  private WorkLease guard() {
    SubjectKey key = key(); var work = new WorkKey("customer", key);
    tx.executeWithoutResult(s -> store.enqueue(work, Priority.URGENT, true, Instant.now()));
    return tx.execute(s -> store.claim(Priority.URGENT, Instant.now(), Duration.ofSeconds(60)).orElseThrow());
  }

  private void sequence(String mode) throws Exception {
    var result = new LinkedHashMap<String, Object>(); result.put("mode", mode); result.put("status", "FAILED"); cases.add(result);
    var measurements = new ArrayList<Map<String, Object>>(); result.put("attempts", measurements); write();
    SubjectKey key = key(); var p = prepare(key, !mode.equals("insert-race")); var guard = guard();
    SubjectKey extra = null;
    if (mode.equals("flush-extra-row")) { extra = key(); try (var c = observer.getConnection()) { insert(c, extra); } }
    trace.deferLookup = mode.equals("jpa-refresh"); trace.extra = extra;
    trace.dmlNowait = mode.equals("flush-extra-row") || mode.equals("insert-race");
    result.put("deferred_lookup_isolation_fixture", trace.deferLookup);
    result.put("dml_nowait_prototype", trace.dmlNowait);
    Throwable lastFailure = null;
    try (var blocker = observer.getConnection()) {
      blocker.setAutoCommit(false);
      if (mode.equals("insert-race")) insert(blocker, key);
      else {
        String sql = mode.equals("jdbc-point") ? "update MS_WORK set last_error_code='held' where work_id=?"
            : "update SAMPLE_CUSTOMER set display_name='held' where storage_key=?";
        try (var s = blocker.prepareStatement(sql)) {
          s.setString(1, CustomerJpaMapping.storageId(extra == null ? key : extra)); assertEquals(1, s.executeUpdate());
        }
      }
      result.put("blocker_released_for_proof", false);
      try {
        for (int n = 0; n < 3; n++) {
          trace.submitted.clear(); trace.updates = 0;
          Future<NowaitAttempt> future = executor.submit(() -> attempt(mode, p, guard));
          NowaitAttempt attempt;
          try { attempt = future.get(5, TimeUnit.SECONDS); }
          catch (TimeoutException timeout) { unresolved = true; result.put("uncertain_operation", true); write(); throw timeout; }
          measurements.add(attempt.evidence()); write(); lastFailure = attempt.failure();
          assertTrue(oracle54(attempt.failure()), "Expected real Oracle NOWAIT refusal, not transport/SQL guard failure");
          @SuppressWarnings("unchecked") var refused = (Map<String, Object>) attempt.evidence().get("server_at_refusal_before_rollback");
          assertEquals(0, ((Number) refused.get("LOCK_WAIT")).intValue());
          assertEquals(1, ((Number) refused.get("TRANSACTION_ACTIVE")).intValue(), "NOWAIT statement refusal is not whole transaction rollback");
          @SuppressWarnings("unchecked") var submitted = (List<String>) attempt.evidence().get("sql_submitted");
          assertEquals(submitted.size(), attempt.evidence().get("sql_count_at_refusal"), "No SQL may follow refusal before rollback");
          assertEquals(TransactionSynchronization.STATUS_ROLLED_BACK, attempt.evidence().get("transaction_completion"));
          assertEquals(0, physical.get()); assertEquals(0, source.getHikariPoolMXBean().getActiveConnections());
          Map<String, Object> after = session(attempt.sid(), attempt.serial());
          attempt.evidence().put("server_after_rollback", after);
          assertEquals(0, ((Number) after.get("LOCK_WAIT")).intValue());
          assertEquals(0, ((Number) after.get("TRANSACTION_ACTIVE")).intValue());
          assertEquals("LEASED", view.queryForObject("select work_status from MS_WORK where work_id=?", String.class, p.lease().key().storageId()));
          assertEquals(p.lease().token(), view.queryForObject("select lease_token from MS_WORK where work_id=?", String.class, p.lease().key().storageId()));
          assertEquals(p.lease().generation(), view.queryForObject("select generation from MS_WORK where work_id=?", Long.class, p.lease().key().storageId()));
          assertEquals(0, view.queryForObject("select count(*) from MS_STATE where work_id=?", Integer.class, p.lease().key().storageId()));
          assertNull(view.queryForObject("select last_error_code from MS_WORK where work_id=?", String.class, guard.key().storageId()));
          try (var c = observer.getConnection(); var s = c.prepareStatement("select work_id from MS_WORK where work_id=? for update nowait")) {
            c.setAutoCommit(false); s.setString(1, guard.key().storageId()); try (var rows = s.executeQuery()) { assertTrue(rows.next()); } c.rollback();
          }
          try (var healthy = source.getConnection(); var s = healthy.createStatement(); var rows = s.executeQuery("select 1 from dual")) {
            assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
          }
          attempt.evidence().put("earlier_guard_write_rolled_back_and_lock_released", true);
          attempt.evidence().put("pool_active_after", source.getHikariPoolMXBean().getActiveConnections());
          attempt.evidence().put("healthy_subsequent_query", true);
          attempt.evidence().put("intent_token_generation_preserved", true);
          if (mode.equals("jdbc-point") || mode.equals("jpa-lookup") || mode.equals("jpa-refresh")) {
            assertEquals(0, trace.updates);
            assertTrue(trace.submitted.stream().noneMatch(sql -> sql.toLowerCase(Locale.ROOT).startsWith("update sample_customer")));
            attempt.evidence().put("projection_dml_after_refusal", 0);
          }
          assertFalse(blocker.getAutoCommit()); assertFalse(blocker.isClosed()); write();
        }
        if (!mode.equals("jdbc-point")) {
          coordinator.failed(p.lease(), lastFailure);
          result.put("retry", view.queryForMap("select work_status,attempts,last_error_code,next_at_ms from MS_WORK where work_id=?", p.lease().key().storageId()));
          assertEquals("WAITING", view.queryForObject("select work_status from MS_WORK where work_id=?", String.class, p.lease().key().storageId()));
          assertEquals(1, view.queryForObject("select attempts from MS_WORK where work_id=?", Integer.class, p.lease().key().storageId()));
        } else {
          Throwable originalFailure = lastFailure;
          Throwable failure = assertThrows(Exception.class, () -> coordinator.failed(p.lease(), originalFailure));
          assertTrue(oracle54(failure)); result.put("retry_metadata_refused_while_same_ledger_lock_held", true);
        }
        result.put("status", "REFUSAL_PROOF_PASS");
      } finally {
        result.put("physical_active_before_cleanup", physical.get());
        result.put("emergency_release", physical.get() != 0);
        blocker.rollback(); result.put("blocker_release_is_cleanup_only", true); write();
      }
    }
    trace.reset();
    // Explicit fixture expiry/due transition after proof, not a cancellation mechanism.
    view.update("update MS_WORK set lease_until_ms=0,next_at_ms=0 where work_id=?", p.lease().key().storageId());
    var lease = coordinator.claim().orElseThrow(); assertEquals(key, lease.key().subject());
    assertThrows(LeaseLost.class, () -> coordinator.finish(p, FetchResult.found(new Customer("obsolete", "ACTIVE"), "obsolete")));
    var recovered = coordinator.prepare(lease).orElseThrow();
    tx.executeWithoutResult(status -> { em.setProperty("jakarta.persistence.lock.timeout", 0);
      coordinator.finish(recovered, FetchResult.found(new Customer("after", "ACTIVE"), "after")); });
    assertEquals("after", view.queryForObject("select display_name from SAMPLE_CUSTOMER where storage_key=?", String.class, p.lease().key().storageId()));
    assertEquals("IDLE", view.queryForObject("select work_status from MS_WORK where work_id=?", String.class, p.lease().key().storageId()));
    result.put("post_cleanup_recovery_and_old_token_rejection", true); result.put("status", "PROOF_PASS"); write();
  }

  private NowaitAttempt attempt(String mode, PreparedSync<?> p, WorkLease guard) {
    Map<String, Object> evidence = new LinkedHashMap<>(); var receipt = new NowaitTransactionReceipt();
    int[] identity = new int[2]; Connection[] physicalConnection = new Connection[1]; long start = System.nanoTime(); Throwable failure = null;
    physical.incrementAndGet();
    try {
      tx.executeWithoutResult(status -> {
        em.setProperty("jakarta.persistence.lock.timeout", 0);
        TransactionSynchronizationManager.registerSynchronization(receipt);
        physicalConnection[0] = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Connection>) c -> c.unwrap(Connection.class));
        Map<String, Object> values = jdbc.queryForMap("select sid,serial# from v$session where sid=to_number(sys_context('USERENV','SID'))");
        identity[0] = ((Number) values.get("SID")).intValue(); identity[1] = ((Number) values.get("SERIAL#")).intValue();
        assertTrue(store.lockValid(guard, Instant.now()));
        jdbc.update("update MS_WORK set last_error_code='attempt-marker' where work_id=?", guard.key().storageId());
        try {
          if (mode.equals("jdbc-point")) store.lockValid(p.lease(), Instant.now());
          else coordinator.finish(p, FetchResult.found(new Customer("attempt", "ACTIVE"), "attempt"));
          throw new AssertionError("Contended operation unexpectedly succeeded");
        } catch (RuntimeException refusal) {
          evidence.put("client_refusal_ms", elapsed(start)); evidence.put("sql_count_at_refusal", trace.submitted.size());
          evidence.put("server_at_refusal_before_rollback", session(identity[0], identity[1]));
          throw refusal;
        }
      });
    } catch (Exception error) { failure = error; evidence.put("error_chain", errors(error)); }
    finally { physical.decrementAndGet(); }
    evidence.put("transaction_end_ms", elapsed(start)); evidence.put("transaction_completion", receipt.status);
    evidence.put("server_session", Map.of("sid", identity[0], "serial", identity[1]));
    try { evidence.put("physical_connection_closed", physicalConnection[0] == null || physicalConnection[0].isClosed()); }
    catch (SQLException error) { evidence.put("connection_state_error", errors(error)); }
    evidence.put("sql_submitted", List.copyOf(trace.submitted)); evidence.put("mapping_update_calls", trace.updates);
    return new NowaitAttempt(evidence, failure, identity[0], identity[1]);
  }

  private Map<String, Object> session(int sid, int serial) {
    return view.queryForMap("select status,event,state,sql_id,prev_sql_id,case when taddr is null then 0 else 1 end TRANSACTION_ACTIVE,"
        + "case when event='enq: TX - row lock contention' and state='WAITING' then 1 else 0 end LOCK_WAIT"
        + " from v$session where sid=? and serial#=?", sid, serial);
  }
  static boolean oracle54(Throwable error) {
    for (Throwable current = error; current != null; current = current.getCause())
      if (current instanceof SQLException sql && sql.getErrorCode() == 54) return true;
    return false;
  }
  private static List<Map<String, Object>> errors(Throwable error) {
    var values = new ArrayList<Map<String, Object>>();
    for (Throwable current = error; current != null && values.size() < 10; current = current.getCause()) {
      Map<String, Object> value = new LinkedHashMap<>(); value.put("type", current.getClass().getName());
      if (current instanceof SQLException sql) value.put("oracle_code", sql.getErrorCode()); values.add(value);
    }
    return values;
  }
  private static long elapsed(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start); }
  private void write() throws Exception { FaultEvidence.write("oracle-nowait-diagnostic.json", report); }

  @Test void queueSkipLockedIsNotRewritten() throws Exception {
    var blocked = key(); var free = key();
    gateway.request("customer", blocked.tenant(), blocked.id(), Priority.NORMAL, true);
    gateway.request("customer", free.tenant(), free.id(), Priority.NORMAL, true);
    try (var blocker = observer.getConnection(); var s = blocker.prepareStatement("select work_id from MS_WORK where work_id=? for update")) {
      blocker.setAutoCommit(false); s.setString(1, CustomerJpaMapping.storageId(blocked)); try (var rows = s.executeQuery()) { assertTrue(rows.next()); }
      var claimed = coordinator.claim().orElseThrow(); assertEquals(free, claimed.key().subject());
      try { assertTrue(Boolean.TRUE.equals(tx.execute(status -> store.lockValid(claimed, Instant.now())))); }
      finally {
        // A claim-only control must release its local provider reservation as a real worker does.
        // Defer without owner admission; row cleanup below is not a coordinator disposition.
        coordinator.failed(claimed, new RetryLater(Instant.now().plusSeconds(60)));
      }
      var next = key(); gateway.request("customer", next.tenant(), next.id(), Priority.URGENT, true);
      var followup = coordinator.claim().orElseThrow();
      try { assertEquals(next, followup.key().subject(), "A prior control must not strand the next case's claim"); }
      finally { coordinator.failed(followup, new RetryLater(Instant.now().plusSeconds(60))); }
      var selectedSql = view.queryForList("select sql_id,child_number,sql_fulltext,executions from v$sql "
          + "where parsing_schema_name=user and upper(sql_text) like 'SELECT%MS_WORK%FOR UPDATE SKIP LOCKED%' "
          + "and executions>0");
      assertFalse(selectedSql.isEmpty(), "Executed queue SQL must be retained separately from point-lock SQL");
      var evidence = new LinkedHashMap<String, Object>();
      evidence.put("mode", "queue-skip-locked-separate"); evidence.put("status", "PROOF_PASS");
      evidence.put("followup_claim_after_disposition", true);
      evidence.put("executed_queue_sql", selectedSql.stream().map(row -> Map.of("sql_id", row.get("SQL_ID"),
          "child_number", row.get("CHILD_NUMBER"), "sql", view.queryForObject(
              "select sql_fulltext from v$sql where sql_id=? and child_number=?", String.class,
              row.get("SQL_ID"), row.get("CHILD_NUMBER")), "cumulative_executions_not_window_delta", row.get("EXECUTIONS"))).toList());
      cases.add(evidence); blocker.rollback();
    }
    write();
  }

  @Test void successRetainsGenerationFencingAndLocalRevisionChecks() throws Exception {
    var key = key(); var p = prepare(key, true);
    trace.submitted.clear();
    gateway.request("customer", key.tenant(), key.id(), Priority.NORMAL, true);
    tx.executeWithoutResult(status -> { em.setProperty("jakarta.persistence.lock.timeout", 0);
      coordinator.finish(p, FetchResult.found(new Customer("after", "ACTIVE"), "after")); });
    assertEquals("WAITING", view.queryForObject("select work_status from MS_WORK where work_id=?", String.class, p.lease().key().storageId()));
    assertTrue(trace.submitted.stream().filter(sql -> sql.toLowerCase(Locale.ROOT).contains("for update")).allMatch(sql -> sql.toLowerCase(Locale.ROOT).endsWith("nowait")));
    assertTrue(trace.submitted.stream().anyMatch(sql -> sql.toLowerCase(Locale.ROOT).startsWith("update sample_customer")));
    assertThrows(LeaseLost.class, () -> coordinator.finish(p, FetchResult.found(new Customer("obsolete", "ACTIVE"), "obsolete")));
    var newer = coordinator.prepare(coordinator.claim().orElseThrow()).orElseThrow();
    view.update("update SAMPLE_CUSTOMER set display_name='local-change',local_version=local_version+1 where storage_key=?", p.lease().key().storageId());
    coordinator.finish(newer, FetchResult.found(new Customer("must-not-apply", "ACTIVE"), "wrong"));
    assertEquals("local-change", view.queryForObject("select display_name from SAMPLE_CUSTOMER where storage_key=?", String.class, p.lease().key().storageId()));
    assertEquals("LOCAL_CONFLICT", view.queryForObject("select last_error_code from MS_WORK where work_id=?", String.class, p.lease().key().storageId()));
    cases.add(new LinkedHashMap<>(Map.of("mode", "success-generation-fencing-revision", "status", "PROOF_PASS", "sql_submitted", List.copyOf(trace.submitted)))); write();
  }

  @AfterEach void cleanupRows() throws Exception {
    // Only owned test rows, and only after physical completion; emergency release cannot qualify a case.
    assertEquals(0, physical.get(), "Unresolved physical operation; no replacement attempts authorized");
    trace.reset();
    for (String id : owned) { view.update("delete from MS_STATE where work_id=?", id); view.update("delete from MS_WORK where work_id=?", id); view.update("delete from SAMPLE_CUSTOMER where storage_key=?", id); }
    owned.clear();
  }
  @AfterAll void close() throws Exception {
    executor.shutdown(); boolean ended = executor.awaitTermination(12, TimeUnit.SECONDS);
    if (context != null) { String pool = source.getPoolName(); context.close(); FaultEvidence.awaitNoPoolThreads(pool); }
    if (observer != null) {
      if (tableOwned) { view.execute("drop table SAMPLE_CUSTOMER purge");
        assertEquals(0, view.queryForObject("select count(*) from user_tables where table_name='SAMPLE_CUSTOMER'", Integer.class));
        view.update("delete from MS_QUOTA where quota_group='nowait-owner'"); }
      observer.close(); FaultEvidence.awaitNoPoolThreads(observer.getPoolName());
    }
    report.put("owned_executor_terminated", ended); report.put("physical_remaining", physical.get());
    report.put("owned_pools_closed", source != null && source.isClosed() && observer.isClosed()); report.put("table_removed", tableOwned);
    report.put("status", cases.size() == 7 && cases.stream().allMatch(c -> "PROOF_PASS".equals(c.get("status"))) && ended ? "COLLECTED" : "FAILED"); write();
    assertTrue(ended);
  }
}
