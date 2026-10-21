package io.modelsync.jdbc;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fault injection at the teardown DDL boundary; no production persistence capability. */
final class CleanupRecordingJdbcTemplate extends JdbcTemplate {
  final List<String> statements = new ArrayList<>();
  final RuntimeException truncateFailure = new IllegalStateException("injected truncate failure");
  final RuntimeException restorationFailure = new IllegalStateException("injected restoration failure");
  boolean failTruncate;
  boolean failRestoration;

  @Override public void execute(String sql) {
    statements.add(sql);
    if (failTruncate && sql.equals("truncate table MS_WORK")) throw truncateFailure;
    if (failRestoration && sql.startsWith("alter table MS_STATE enable validate")) throw restorationFailure;
  }
}
