package io.modelsync.sample;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/** Read-only SQL capture and owned-pool identity inventory; never changes submitted SQL. */
final class LockPolicyObservation implements StatementInspector {
  final List<String> sql = new CopyOnWriteArrayList<>();
  final AtomicInteger ownerCalls = new AtomicInteger();
  final List<Map<String, Integer>> runtimeSessions = new ArrayList<>();
  Runnable beforeProjectionUpdate;
  LockPolicyObservation(DataSource source) throws SQLException {
    List<Connection> connections = new ArrayList<>();
    try {
      for (int n = 0; n < 3; n++) {
        Connection c = source.getConnection(); connections.add(c);
        try (var s = c.createStatement(); var rows = s.executeQuery(
            "select sid,serial# from v$session where sid=to_number(sys_context('USERENV','SID'))")) {
          if (!rows.next()) throw new SQLException("Owned pool identity absent");
          runtimeSessions.add(Map.of("sid", rows.getInt(1), "serial", rows.getInt(2)));
        }
      }
    } finally { for (Connection connection : connections) connection.close(); }
  }
  @Override public String inspect(String statement) {
    sql.add(statement);
    if (beforeProjectionUpdate != null && statement.toLowerCase(Locale.ROOT).startsWith("update sample_customer"))
      beforeProjectionUpdate.run();
    return statement;
  }
}
