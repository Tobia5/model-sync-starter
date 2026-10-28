package io.modelsync.sample;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** Narrow prototype seam for the real JdbcWorkStore's one private point-lock query. */
final class NowaitPointJdbcTemplate extends JdbcTemplate {
  private final NowaitSqlProbe trace;
  NowaitPointJdbcTemplate(DataSource source, NowaitSqlProbe trace) { super(source); this.trace = trace; setQueryTimeout(2); }

  @Override public <T> java.util.List<T> query(String sql, RowMapper<T> mapper, Object... args) {
    String actual = NowaitSqlProbe.point(sql);
    trace.submitted.add(actual);
    return super.query(actual, mapper, args);
  }
}
