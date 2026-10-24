package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.modelsync.core.domain.*;
import io.modelsync.core.port.LocalContention;
import io.modelsync.jdbc.*;
import java.sql.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

class InsertPolicyTest {
  private JdbcTemplate jdbc(int major,int minor,String version) throws Exception {
    var jdbc=mock(JdbcTemplate.class);var connection=mock(Connection.class);var metadata=mock(DatabaseMetaData.class);
    when(connection.getMetaData()).thenReturn(metadata);when(metadata.getDatabaseProductName()).thenReturn("Oracle");
    when(metadata.getDatabaseMajorVersion()).thenReturn(major);when(metadata.getDatabaseMinorVersion()).thenReturn(minor);when(metadata.getDatabaseProductVersion()).thenReturn(version);
    when(jdbc.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Boolean>>any())).thenAnswer(i->i.<ConnectionCallback<Boolean>>getArgument(0).doInConnection(connection));return jdbc;
  }
  private PlatformTransactionManager manager() {var manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());return manager;}
  @Test void capabilityRequiresQualifiedMetadataWithoutChangingDefault() throws Exception {
    assertDoesNotThrow(()->new JdbcWorkStore(jdbc(23,26,"Oracle AI Database 26ai Free Release23.26.3"),JdbcPointLockPolicy.DEFAULT,JdbcInsertPolicy.ORACLE_26_NOWAIT));
    for(var coordinates:new int[][]{{19,0},{23,0},{23,25},{23,27},{26,0}}) {
      var jdbc=jdbc(coordinates[0],coordinates[1],"Oracle Database unqualified line");assertDoesNotThrow(()->new JdbcWorkStore(jdbc));
      assertThrows(IllegalArgumentException.class,()->new JdbcWorkStore(jdbc,JdbcPointLockPolicy.DEFAULT,JdbcInsertPolicy.ORACLE_26_NOWAIT));
    }
  }
  @Test void h2IsNotNativeOracleQualification() {
    var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:insert-policy","sa",""));assertDoesNotThrow(()->new JdbcWorkStore(jdbc));
    assertThrows(IllegalArgumentException.class,()->new JdbcWorkStore(jdbc,JdbcPointLockPolicy.DEFAULT,JdbcInsertPolicy.ORACLE_26_NOWAIT));
    assertThrows(IllegalArgumentException.class,()->new JdbcQuotaGate(jdbc,manager(),Clock.systemUTC(),JdbcQuotaLockPolicy.DEFAULT,JdbcInsertPolicy.ORACLE_26_NOWAIT));
  }
  @Test void explicitAndPassiveCreationUseNativeSqlAndDetachRefusal() throws Exception {
    var jdbc=jdbc(23,26,"Oracle AI Database 26ai");var original=new CannotAcquireLockException("synthetic",new SQLException("redacted","61000",54));
    doThrow(original).when(jdbc).update(anyString(),any(Object[].class));
    var store=new JdbcWorkStore(jdbc,JdbcPointLockPolicy.ORACLE_NOWAIT,JdbcInsertPolicy.ORACLE_26_NOWAIT);var key=new WorkKey("synthetic",new SubjectKey("tenant","subject"));
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      var explicit=assertThrows(LocalContention.class,()->store.enqueue(key,Priority.NORMAL,true,Instant.now()));var passive=assertThrows(LocalContention.class,()->store.enqueueRefresh(key,Priority.NORMAL,Instant.now()));
      for(var failure:new LocalContention[]{explicit,passive}){var cause=assertInstanceOf(SQLException.class,failure.getCause());assertEquals(54,cause.getErrorCode());assertNull(cause.getCause());assertNull(cause.getNextException());}
      var sql=org.mockito.ArgumentCaptor.forClass(String.class);verify(jdbc,times(2)).update(sql.capture(),any(Object[].class));assertTrue(sql.getAllValues().stream().allMatch(s->s.startsWith("insert into MS_WORK ")&&s.endsWith(" nowait")));
    }finally{TransactionSynchronizationManager.clear();}
  }
  @Test void quotaCreationRefusalRollsBackBeforeReportingFailure() throws Exception {
    var jdbc=jdbc(23,26,"Oracle AI Database 26ai");var manager=manager();var original=new CannotAcquireLockException("synthetic",new SQLException("redacted","61000",54));doThrow(original).when(jdbc).update(anyString(),any(Object[].class));
    var gate=new JdbcQuotaGate(jdbc,manager,Clock.systemUTC(),JdbcQuotaLockPolicy.ORACLE_NOWAIT,JdbcInsertPolicy.ORACLE_26_NOWAIT);
    assertThrows(LocalContention.class,()->gate.register("synthetic",Duration.ofMillis(1)));verify(manager).rollback(any());verify(manager,never()).commit(any());
    verify(jdbc).update("insert into MS_QUOTA (quota_group,spacing_ms,next_allowed_ms) values (?,?,0) nowait","synthetic",1L);verify(jdbc,never()).queryForObject(anyString(),eq(Long.class),anyString());
  }
  @Test void unrelatedErrorsAndDefaultSqlRetainTheirMeaning() throws Exception {
    var jdbc=jdbc(23,26,"Oracle AI Database 26ai");var manager=manager();var gate=new JdbcQuotaGate(jdbc,manager,Clock.systemUTC(),JdbcQuotaLockPolicy.DEFAULT,JdbcInsertPolicy.ORACLE_26_NOWAIT);
    for(int code:new int[]{1,1013,17002}){var original=new DataAccessResourceFailureException("synthetic",new SQLException("redacted","72000",code));doThrow(original).when(jdbc).update(anyString(),any(Object[].class));assertSame(original,assertThrows(DataAccessException.class,()->gate.register("synthetic",Duration.ofMillis(1))));}
    var compatible=jdbc(19,0,"Oracle Database19c");var original=new CannotAcquireLockException("synthetic",new SQLException("redacted","61000",54));doThrow(original).when(compatible).update(anyString(),any(Object[].class));
    assertSame(original,assertThrows(CannotAcquireLockException.class,()->new JdbcQuotaGate(compatible,manager,Clock.systemUTC()).register("synthetic",Duration.ofMillis(1))));verify(compatible).update("insert into MS_QUOTA (quota_group,spacing_ms,next_allowed_ms) values (?,?,0)","synthetic",1L);
  }
}
