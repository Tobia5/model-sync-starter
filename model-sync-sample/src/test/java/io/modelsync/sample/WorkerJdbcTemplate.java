package io.modelsync.sample;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** Completed selector callbacks, separate from worker ticks and Oracle native counters. */
final class WorkerJdbcTemplate extends JdbcTemplate {
  final AtomicLong selectors = new AtomicLong();
  WorkerJdbcTemplate(DataSource source) { super(source); }
  @Override public <T> T execute(ConnectionCallback<T> action) {
    T result = super.execute(action);
    if (result instanceof Optional<?>) selectors.incrementAndGet();
    return result;
  }
}
