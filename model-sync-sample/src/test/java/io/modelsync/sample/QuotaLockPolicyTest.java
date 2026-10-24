package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.modelsync.core.port.LocalContention;
import io.modelsync.jdbc.*;
import java.sql.SQLException;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

class QuotaLockPolicyTest {
  private JdbcTemplate jdbc() {
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Boolean>>any())).thenReturn(true);
    return jdbc;
  }
  private PlatformTransactionManager manager() {
    var manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus()); return manager;
  }
  @SuppressWarnings("unchecked")
  @Test void acquireRefusesOnlyAfterRollbackAndNeverRunsTheUpdate() {
    var jdbc = jdbc(); var manager = manager();
    when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq("synthetic")))
        .thenThrow(new CannotAcquireLockException("synthetic", new SQLException("redacted", "61000", 54)));
    var gate = new JdbcQuotaGate(jdbc, manager, Clock.systemUTC(), JdbcQuotaLockPolicy.ORACLE_NOWAIT);
    var failure = assertThrows(LocalContention.class, () -> gate.acquire("synthetic"));
    verify(manager).rollback(any()); verify(manager, never()).commit(any());
    verify(jdbc).queryForObject(eq("select spacing_ms,next_allowed_ms from MS_QUOTA where quota_group=? for update nowait"), any(RowMapper.class), eq("synthetic"));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
    var cause = assertInstanceOf(SQLException.class, failure.getCause());
    assertEquals(54, cause.getErrorCode()); assertNull(cause.getCause()); assertNull(cause.getNextException());
  }
  @Test void cooldownRefusalDoesNotReportPersistenceOrIssueAnUpdate() {
    var jdbc = jdbc(); var manager = manager();
    when(jdbc.queryForObject(anyString(), eq(Long.class), eq("synthetic")))
        .thenThrow(new CannotAcquireLockException("synthetic", new SQLException("redacted", "61000", 54)));
    var gate = new JdbcQuotaGate(jdbc, manager, Clock.systemUTC(), JdbcQuotaLockPolicy.ORACLE_NOWAIT);
    assertThrows(LocalContention.class, () -> gate.cooldown("synthetic", Instant.now().plusSeconds(10)));
    verify(manager).rollback(any()); verify(manager, never()).commit(any());
    verify(jdbc).queryForObject("select next_allowed_ms from MS_QUOTA where quota_group=? for update nowait", Long.class, "synthetic");
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }
  @Test void unrelatedDatabaseFailuresRetainTheirClassification() {
    var jdbc = jdbc(); var manager = manager();
    var gate = new JdbcQuotaGate(jdbc, manager, Clock.systemUTC(), JdbcQuotaLockPolicy.ORACLE_NOWAIT);
    for (int code : new int[] {1, 1013, 17002}) {
      var original = new DataAccessResourceFailureException("synthetic", new SQLException("redacted", "72000", code));
      doThrow(original).when(jdbc).queryForObject(anyString(), eq(Long.class), eq("synthetic"));
      assertSame(original, assertThrows(DataAccessException.class, () -> gate.cooldown("synthetic", Instant.now())));
    }
  }
  @SuppressWarnings("unchecked")
  @Test void successfulCallbackIsNotAdmissionWhenCommitFails() {
    var jdbc = jdbc(); var manager = manager();
    when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq("synthetic"))).thenReturn(new long[] {1000, 0});
    when(jdbc.queryForObject(anyString(), eq(Long.class), eq("synthetic"))).thenReturn(0L);
    var failure = new TransactionSystemException("synthetic uncertain commit");
    doThrow(failure).when(manager).commit(any());
    var gate = new JdbcQuotaGate(jdbc, manager, Clock.systemUTC(), JdbcQuotaLockPolicy.ORACLE_NOWAIT);
    assertSame(failure, assertThrows(TransactionSystemException.class, () -> gate.acquire("synthetic")));
    assertSame(failure, assertThrows(TransactionSystemException.class, () -> gate.cooldown("synthetic", Instant.now())));
  }
  @Test void defaultIndependentTransactionsCommitAndCooldownNeverShortens() {
    var source = new DriverManagerDataSource("jdbc:h2:mem:quota-policy;DB_CLOSE_DELAY=-1", "sa", "");
    var jdbc = new JdbcTemplate(source); jdbc.execute("create table MS_QUOTA(quota_group varchar(128) primary key,spacing_ms bigint,next_allowed_ms bigint)");
    var manager = new DataSourceTransactionManager(source);
    var now = Instant.parse("2026-10-24T00:00:00Z");
    var gate = new JdbcQuotaGate(jdbc, manager, Clock.fixed(now, ZoneOffset.UTC));
    gate.register("synthetic", Duration.ofSeconds(1));
    new TransactionTemplate(manager).executeWithoutResult(status -> {
      assertTrue(gate.acquire("synthetic").granted()); status.setRollbackOnly();
    });
    assertEquals(now.plusSeconds(1), gate.nextAdmission("synthetic").orElseThrow());
    assertFalse(gate.acquire("synthetic").granted());
    gate.cooldown("synthetic", now.plusSeconds(30)); gate.cooldown("synthetic", now.plusSeconds(2));
    assertEquals(now.plusSeconds(30), gate.acquire("synthetic").retryAt());
    assertThrows(IllegalStateException.class, () -> gate.register("synthetic", Duration.ofSeconds(2)));
    assertThrows(IllegalArgumentException.class, () -> new JdbcQuotaGate(jdbc, manager, Clock.systemUTC(), JdbcQuotaLockPolicy.ORACLE_NOWAIT));
  }
  @Test void defaultSqlAndErrorsAreCompatible() {
    var jdbc = jdbc(); var manager = manager(); var original = new CannotAcquireLockException("synthetic", new SQLException("redacted", "61000", 54));
    when(jdbc.queryForObject(anyString(), eq(Long.class), eq("synthetic"))).thenThrow(original);
    assertSame(original, assertThrows(CannotAcquireLockException.class, () -> new JdbcQuotaGate(jdbc, manager, Clock.systemUTC()).cooldown("synthetic", Instant.now())));
    verify(jdbc).queryForObject("select next_allowed_ms from MS_QUOTA where quota_group=? for update", Long.class, "synthetic");
  }
}
