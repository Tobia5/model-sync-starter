package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcPointLockPolicyTest {
  @Test void defaultAndOptInHaveOnlyAnExplicitPointLockDifference() {
    assertEquals("select * from MS_WORK where work_id=? for update", JdbcPointLockPolicy.DEFAULT.sql());
    assertEquals(JdbcPointLockPolicy.DEFAULT.sql() + " nowait", JdbcPointLockPolicy.ORACLE_NOWAIT.sql());
  }
  @Test void onlyExactOracleRefusalIsClassifiedAsContention() {
    assertTrue(JdbcWorkStore.oracleLockRefusal(new IllegalStateException(new SQLException("synthetic", "61000", 54))));
    for (int code : new int[] {1, 1013, 18730, 17002})
      assertFalse(JdbcWorkStore.oracleLockRefusal(new SQLException("synthetic", "72000", code)));
    assertFalse(JdbcWorkStore.oracleLockRefusal(new IllegalStateException("timeout")));
  }
  @Test void oracleOptInRejectsAnUnqualifiedDatabase() {
    var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:oracle-policy-check", "sa", ""));
    assertDoesNotThrow(() -> new JdbcWorkStore(jdbc));
    assertThrows(IllegalArgumentException.class, () -> new JdbcWorkStore(jdbc, JdbcPointLockPolicy.ORACLE_NOWAIT));
  }
}
