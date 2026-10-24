package io.modelsync.sample;

import io.modelsync.core.domain.SubjectKey;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/** Test-only SQL prototype. No consumer SQL or production configuration is rewritten. */
final class NowaitSqlProbe implements StatementInspector {
  final List<String> submitted = new CopyOnWriteArrayList<>();
  volatile boolean deferLookup;
  volatile boolean dmlNowait;
  volatile SubjectKey extra;
  volatile int updates;

  static String point(String sql) {
    return sql.equals("select * from MS_WORK where work_id=? for update") ? sql + " nowait" : sql;
  }

  static String dml(String sql, boolean enabled) {
    String normalized = sql.toLowerCase(java.util.Locale.ROOT);
    if (enabled && (normalized.startsWith("update sample_customer set ")
        || normalized.startsWith("insert into sample_customer ("))) return sql + " nowait";
    return sql;
  }

  @Override public String inspect(String sql) {
    String actual = dml(sql, dmlNowait);
    submitted.add(actual);
    String normalized = actual.toLowerCase(java.util.Locale.ROOT);
    if (normalized.contains("for update") && !normalized.contains("nowait"))
      throw new IllegalStateException("Unbounded fixture SELECT lock rejected before JDBC: " + actual);
    return actual;
  }

  void reset() { submitted.clear(); deferLookup = false; dmlNowait = false; extra = null; updates = 0; }
}
