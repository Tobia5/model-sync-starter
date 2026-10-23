package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit diagnostic only: successful evidence collection never qualifies the failed full gate. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OracleCancellationDiagnostic {
  private HikariDataSource observer;
  private JdbcTemplate jdbc;
  private Map<String, Object> report;
  private final List<Map<String, Object>> cases = new ArrayList<>();
  private boolean tableOwned;
  private int nextKey;

  @BeforeAll void open() throws Exception {
    report = FaultEvidence.report("small-locked-DML-cancellation-DIAGNOSTIC_ONLY");
    report.put("full_qualification", "NOT_RUN"); report.put("cases", cases);
    report.put("query_timeout_seconds", 2); report.put("socket_read_timeout_ms", 10000);
    report.put("observation_boundary_ms", 5000); report.put("network_containment_ms", 3000);
    report.put("connect_timeout_ms", 1500); report.put("disable_oob", true);
    report.put("post_boundary_cleanup_observation_ms", 7000);
    report.put("cleanup_observation_is_not_a_gate_extension", true);
    observer = RecoveryConfiguration.source(System.getenv("MODELSYNC_ORACLE_URL"), 2, 10000);
    jdbc = new JdbcTemplate(observer); jdbc.setQueryTimeout(2);
    assertEquals(0, jdbc.queryForObject("select count(*) from user_tables where table_name='MS_CANCEL_DIAG'", Integer.class));
    jdbc.execute("create table MS_CANCEL_DIAG(fixture_id number primary key, display_value varchar2(40) not null)");
    tableOwned = true;
    try (var connection = observer.getConnection()) {
      report.put("driver", connection.getMetaData().getDriverVersion());
      report.put("database", connection.getMetaData().getDatabaseProductVersion());
    }
    report.put("jdk", System.getProperty("java.runtime.version"));
    report.put("max_heap_bytes", Runtime.getRuntime().maxMemory());
    report.put("spring", org.springframework.core.SpringVersion.getVersion());
    report.put("hikari", HikariDataSource.class.getPackage().getImplementationVersion());
    write();
  }

  @Test void automaticDirect() throws Exception { measure("automatic-query-timeout", "direct-jdbc"); }
  @Test void automaticSpring() throws Exception { measure("automatic-query-timeout", "spring-hikari"); }
  @Test void explicitDirect() throws Exception { measure("explicit-cancel-once", "direct-jdbc"); }
  @Test void explicitSpring() throws Exception { measure("explicit-cancel-once", "spring-hikari"); }
  @Test void containmentDirect() throws Exception { measure("layered-network-containment", "direct-jdbc"); }
  @Test void containmentSpring() throws Exception { measure("layered-network-containment", "spring-hikari"); }

  private void measure(String mode, String path) throws Exception {
    int key = ++nextKey;
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("mode", mode); result.put("path", path); result.put("status", "INCOMPLETE");
    result.put("fixture_rows", 1); cases.add(result); write();
    jdbc.update("insert into MS_CANCEL_DIAG values(?,'before')", key);
    var execution = Executors.newSingleThreadExecutor(r -> new Thread(r, "modelsync-cancel-execution"));
    var canceller = Executors.newSingleThreadExecutor(r -> new Thread(r, "modelsync-cancel-invocation"));
    var network = Executors.newSingleThreadExecutor(r -> new Thread(r, "modelsync-network-timeout"));
    HikariDataSource pool = path.equals("spring-hikari")
        ? RecoveryConfiguration.source(System.getenv("MODELSYNC_ORACLE_URL"), 1, 10000) : null;
    var probe = new LockedDmlProbe(key, mode.equals("layered-network-containment") ? 3000 : 0, network);
    boolean emergencyUnlock = false;
    boolean executionDone = false;
    boolean validated = false;
    try (var blocker = observer.getConnection()) {
      blocker.setAutoCommit(false);
      try (var lock = blocker.prepareStatement("update MS_CANCEL_DIAG set display_value='held' where fixture_id=?")) {
        lock.setInt(1, key); assertEquals(1, lock.executeUpdate());
      }
      int blockerSid;
      try (var query = blocker.createStatement(); var rows = query.executeQuery("select sys_context('USERENV','SID') from dual")) {
        assertTrue(rows.next()); blockerSid = rows.getInt(1);
      }
      var task = execution.submit(() -> {
        if (pool != null) probe.spring(pool);
        else {
          try { probe.direct(direct()); }
          catch (SQLException error) { probe.failure = error; }
        }
      });
      try {
        assertTrue(probe.entered.await(3, TimeUnit.SECONDS), "Physical statement did not enter");
        OracleRecoveryIT.await(() -> jdbc.queryForObject(
            "select count(*) from v$session where sid=? and serial#=? and event='enq: TX - row lock contention' and state='WAITING' and blocking_session=?",
            Integer.class, probe.sid, probe.serial, blockerSid) == 1, 1500);
        result.put("lock_wait_confirmed", true);
        result.put("lock_wait_confirmation_ms", elapsed(probe.startedNs));
        result.put("effective_query_timeout_seconds", probe.effectiveQuerySeconds);
        result.put("effective_network_timeout_ms", probe.effectiveNetworkMs);
        assertEquals(2, probe.effectiveQuerySeconds);
        if (mode.equals("layered-network-containment")) assertEquals(3000, probe.effectiveNetworkMs);
        var cancelEvidence = new java.util.concurrent.ConcurrentHashMap<String, Object>();
        if (mode.equals("explicit-cancel-once")) {
          result.put("manual_cancel", cancelEvidence);
          canceller.submit(() -> {
            cancelEvidence.put("invocations", 1);
            long began = System.nanoTime(); cancelEvidence.put("entered_after_execution_ms", elapsed(probe.startedNs));
            try { probe.statement.cancel(); cancelEvidence.put("outcome", "RETURNED"); }
            catch (SQLException error) { cancelEvidence.put("outcome", errors(error)); }
            finally { cancelEvidence.put("duration_ms", elapsed(began)); cancelEvidence.put("returned", true); }
          });
        }
        long remaining = Math.max(0, 5000 - elapsed(probe.startedNs));
        boolean within = probe.physicalExit.await(remaining, TimeUnit.MILLISECONDS);
        result.put("physical_exit_by_5s", within && probe.exitedNs - probe.startedNs <= TimeUnit.SECONDS.toNanos(5));
        result.put("observed_ms", elapsed(probe.startedNs));
        result.put("owned_stacks_at_boundary", stacks()); write();
        // Keep the competing lock through the original observation boundary and natural read timeout.
        // A later exit is cleanup evidence only, never a retroactive statement-deadline PASS.
        executionDone = probe.physicalExit.await(Math.max(0, 12000 - elapsed(probe.startedNs)), TimeUnit.MILLISECONDS);
        if (!executionDone) { emergencyUnlock = true; blocker.rollback(); }
        task.get(5, TimeUnit.SECONDS); // Wait for rollback/connection disposal independently of execute exit.
        executionDone = probe.physicalExit.getCount() == 0;
        result.put("physical_execution_ms", probe.exitedNs == 0 ? null : TimeUnit.NANOSECONDS.toMillis(probe.exitedNs - probe.startedNs));
        result.put("physical_connection_closed_after_operation", probe.connectionClosedAfter);
        result.put("direct_safe_reuse_after_rollback", probe.directReuseAfterRollback);
        result.put("execution_errors", errors(probe.failure));
        result.put("rollback_errors", errors(probe.rollbackFailure));
        boolean statementCancelled = Boolean.TRUE.equals(result.get("physical_exit_by_5s"))
            && LockedDmlOutcome.hasSqlCode(probe.failure, 1013) && !emergencyUnlock;
        result.put("independent_statement_cancellation", mode.equals("layered-network-containment") ? "NOT_APPLICABLE_CONTAINMENT_ONLY" : statementCancelled ? "PASS" : "FAILED");
        result.put("layered_containment", mode.equals("layered-network-containment")
            ? "PENDING_SAFETY_CHECKS" : "NOT_RUN");
        assertTrue(executionDone);
        result.put("server_session_before_blocker_release", jdbc.queryForList(
            "select status,event,state from v$session where sid=? and serial#=?", probe.sid, probe.serial));
        result.put("server_lock_wait_before_blocker_release", jdbc.queryForObject(
            "select count(*) from v$session where sid=? and serial#=? and event='enq: TX - row lock contention' and state='WAITING'",
            Integer.class, probe.sid, probe.serial) > 0);
        long releaseStart = System.nanoTime();
        blocker.rollback();
        OracleRecoveryIT.await(() -> jdbc.queryForObject(
            "select count(*) from v$session where sid=? and serial#=? and event='enq: TX - row lock contention' and state='WAITING'",
            Integer.class, probe.sid, probe.serial) == 0, 3000);
        result.put("server_lock_settled_after_blocker_release_ms", elapsed(releaseStart));
      } finally {
        result.put("emergency_unlock", emergencyUnlock || !executionDone);
        result.put("blocker_release_credited_as_cancellation", false);
        blocker.rollback(); write();
      }
      assertEquals("before", jdbc.queryForObject("select display_value from MS_CANCEL_DIAG where fixture_id=?", String.class, key));
      result.put("fresh_committed_state", "before");
      // Commit subsequent real work using the measured path, then inspect through a fresh observer.
      try (var next = pool == null ? direct() : pool.getConnection()) {
        next.setAutoCommit(false);
        try (var update = next.prepareStatement("update MS_CANCEL_DIAG set display_value='recovered' where fixture_id=?")) {
          update.setQueryTimeout(2); update.setInt(1, key); assertEquals(1, update.executeUpdate());
        }
        next.commit();
      }
      assertEquals("recovered", jdbc.queryForObject("select display_value from MS_CANCEL_DIAG where fixture_id=?", String.class, key));
      result.put("subsequent_committed_work", true);
      // Collect state/recovery even for an unexpected exception, then fail narrowly.
      assertTrue(LockedDmlOutcome.hasSqlCode(probe.failure, 1013) || LockedDmlOutcome.readTimeout(probe.failure), "Unexpected locked-DML outcome");
      if (LockedDmlOutcome.readTimeout(probe.failure))
        assertTrue(probe.connectionClosedAfter, "Timed-out physical connection must be discarded");
      if (mode.equals("explicit-cancel-once")) {
        @SuppressWarnings("unchecked") var cancel = (Map<String, Object>) result.get("manual_cancel");
        assertEquals(1, cancel.get("invocations")); assertEquals(true, cancel.get("returned"));
      }
      validated = true;
    } finally {
      execution.shutdown(); canceller.shutdown(); network.shutdown();
      result.put("execution_thread_terminated", execution.awaitTermination(5, TimeUnit.SECONDS));
      result.put("cancel_thread_terminated", canceller.awaitTermination(5, TimeUnit.SECONDS));
      result.put("network_executor_terminated", network.awaitTermination(5, TimeUnit.SECONDS));
      if (pool != null) {
        result.put("pool_active_after", pool.getHikariPoolMXBean().getActiveConnections());
        String poolName = pool.getPoolName(); pool.close(); FaultEvidence.awaitNoPoolThreads(poolName);
        result.put("pool_closed", pool.isClosed());
      }
      write();
      assertEquals(true, result.get("execution_thread_terminated"));
      assertEquals(true, result.get("cancel_thread_terminated"));
      assertEquals(true, result.get("network_executor_terminated"));
      if (pool != null) assertEquals(0, result.get("pool_active_after"));
      if (validated) {
        result.put("status", "OBSERVED");
        if (mode.equals("layered-network-containment"))
          result.put("layered_containment", !emergencyUnlock && Boolean.TRUE.equals(result.get("physical_exit_by_5s"))
              && LockedDmlOutcome.readTimeout(probe.failure) ? "CLIENT_IO_CONTAINMENT_PASS" : "NOT_DEMONSTRATED");
      }
      write();
    }
  }

  private static Connection direct() throws SQLException {
    var properties = new Properties(); properties.setProperty("user", System.getenv("MODELSYNC_ORACLE_USER"));
    properties.setProperty("password", System.getenv("MODELSYNC_ORACLE_PASSWORD"));
    properties.setProperty("oracle.net.CONNECT_TIMEOUT", "1500");
    properties.setProperty("oracle.jdbc.ReadTimeout", "10000"); properties.setProperty("oracle.net.disableOob", "true");
    return DriverManager.getConnection(System.getenv("MODELSYNC_ORACLE_URL"), properties);
  }
  private static long elapsed(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start); }
  private static List<Map<String, Object>> errors(Throwable error) {
    List<Map<String, Object>> values = new ArrayList<>();
    for (Throwable current = error; current != null && values.size() < 8; current = current.getCause()) {
      Map<String, Object> value = new LinkedHashMap<>(); value.put("type", current.getClass().getName());
      if (current instanceof SQLException sql) { value.put("oracle_code", sql.getErrorCode()); value.put("sql_state", sql.getSQLState()); }
      values.add(value);
    }
    return values;
  }
  private static Map<String, Object> stacks() {
    Map<String, Object> result = new LinkedHashMap<>();
    Thread.getAllStackTraces().forEach((thread, frames) -> {
      if (thread.getName().startsWith("modelsync-cancel-") || thread.getName().contains("OracleTimeout"))
        result.put(thread.getName(), java.util.Arrays.stream(frames).limit(18).map(StackTraceElement::toString).toList());
    });
    return result;
  }
  private void write() throws Exception { FaultEvidence.write("oracle-cancellation-diagnostic.json", report); }
  @AfterAll void close() throws Exception {
    if (tableOwned) {
      jdbc.execute("drop table MS_CANCEL_DIAG purge");
      assertEquals(0, jdbc.queryForObject("select count(*) from user_tables where table_name='MS_CANCEL_DIAG'", Integer.class));
      report.put("table_removed", true);
    }
    if (observer != null) {
      assertEquals(0, observer.getHikariPoolMXBean().getActiveConnections());
      String poolName = observer.getPoolName(); observer.close(); FaultEvidence.awaitNoPoolThreads(poolName);
    }
    report.put("observer_pool_closed", observer != null && observer.isClosed());
    report.put("status", cases.size() == 6 && cases.stream().allMatch(c -> "OBSERVED".equals(c.get("status"))) ? "COLLECTED" : "FAILED");
    write();
  }
}
