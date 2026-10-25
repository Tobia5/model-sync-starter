package io.modelsync.jdbc;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.*;
import org.springframework.dao.DataAccessException;

/**
 * Scoped fixture telemetry: the production/legacy claim callbacks each execute one selector SQL
 * and return Optional. Metadata returns Set; other fixture SQL uses ordinary JdbcTemplate methods.
 * Counts completed selector queries, including empty results/rolled-back claims, not admitted calls.
 */
final class SelectorJdbcTemplate extends JdbcTemplate {
  final AtomicLong selectorQueries = new AtomicLong();
  CooldownObservation cooldownObservation;
  SelectorJdbcTemplate(DataSource source) { super(source); }
  @Override public <T> T execute(ConnectionCallback<T> action) {
    T result=super.execute(action);
    if(result instanceof Optional<?>)selectorQueries.incrementAndGet();
    return result;
  }
  @Override public <T>T query(PreparedStatementCreator creator,PreparedStatementSetter setter,ResultSetExtractor<T> extractor) {
    try{return super.query(creator,setter,extractor);}catch(DataAccessException failure){if(cooldownObservation!=null)cooldownObservation.sqlFailure(failure);throw failure;}
  }
  @Override protected int update(PreparedStatementCreator creator,PreparedStatementSetter setter) {
    try{return super.update(creator,setter);}catch(DataAccessException failure){if(cooldownObservation!=null)cooldownObservation.sqlFailure(failure);throw failure;}
  }
}
