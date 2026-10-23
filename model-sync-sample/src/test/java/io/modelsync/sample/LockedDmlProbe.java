package io.modelsync.sample;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.zaxxer.hikari.HikariDataSource;

/** Test-owned physical JDBC call. No Future cancellation is used as completion evidence. */
final class LockedDmlProbe {
  static final String SQL = "update MS_CANCEL_DIAG set display_value='must-rollback' where fixture_id=?";
  final CountDownLatch entered = new CountDownLatch(1);
  final CountDownLatch physicalExit = new CountDownLatch(1);
  volatile PreparedStatement statement;
  volatile Connection physicalConnection;
  volatile long startedNs;
  volatile long exitedNs;
  volatile int sid;
  volatile int effectiveQuerySeconds;
  volatile int effectiveNetworkMs;
  volatile Throwable failure;
  volatile Throwable rollbackFailure;
  volatile boolean connectionClosedAfter;
  volatile boolean directReuseAfterRollback;
  private final int key;
  private final int networkMs;
  private final Executor networkExecutor;

  LockedDmlProbe(int key, int networkMs, Executor networkExecutor) {
    this.key = key; this.networkMs = networkMs; this.networkExecutor = networkExecutor;
  }

  void direct(Connection connection) {
    try (connection) {
      connection.setAutoCommit(false);
      configure(connection);
      try (var prepared = connection.prepareStatement(SQL)) {
        prepared.setQueryTimeout(2);
        execute(prepared);
      } catch (SQLException error) { failure = error; }
      finally {
        connectionClosedAfter = connection.isClosed();
        if (!connectionClosedAfter) {
          try {
            connection.rollback();
            try (var check = connection.createStatement(); var rows = check.executeQuery("select 1 from dual")) {
              directReuseAfterRollback = rows.next() && rows.getInt(1) == 1;
            }
          } catch (SQLException error) { rollbackFailure = error; }
        }
      }
    } catch (Exception error) { if (failure == null) failure = error; else rollbackFailure = error; }
  }

  void spring(HikariDataSource source) {
    var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(2);
    var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    try {
      tx.executeWithoutResult(status -> {
        try {
          jdbc.execute(SQL, (PreparedStatementCallback<Integer>) prepared -> {
            configure(prepared.getConnection());
            return execute(prepared);
          });
        } finally { status.setRollbackOnly(); }
      });
    } catch (Exception error) { if (failure == null) failure = error; else rollbackFailure = error; }
    finally {
      if (physicalConnection != null) {
        try { connectionClosedAfter = physicalConnection.isClosed(); }
        catch (SQLException error) { rollbackFailure = error; }
      }
    }
  }

  private void configure(Connection connection) throws SQLException {
    physicalConnection = connection.unwrap(Connection.class);
    if (networkMs > 0) connection.setNetworkTimeout(networkExecutor, networkMs);
    effectiveNetworkMs = connection.getNetworkTimeout();
    try (var check = connection.createStatement(); var rows = check.executeQuery("select sys_context('USERENV','SID') from dual")) {
      if (!rows.next()) throw new SQLException("Missing test session identity");
      sid = rows.getInt(1);
    }
  }

  private int execute(PreparedStatement prepared) throws SQLException {
    statement = prepared;
    effectiveQuerySeconds = prepared.getQueryTimeout();
    prepared.setInt(1, key);
    startedNs = System.nanoTime(); entered.countDown();
    try { return prepared.executeUpdate(); }
    catch (SQLException error) { failure = error; throw error; }
    finally { exitedNs = System.nanoTime(); physicalExit.countDown(); }
  }
}
