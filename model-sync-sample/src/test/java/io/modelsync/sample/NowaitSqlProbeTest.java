package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NowaitSqlProbeTest {
  @Test void onlyExactLedgerPointQueryIsRewritten() {
    assertEquals("select * from MS_WORK where work_id=? for update nowait",
        NowaitSqlProbe.point("select * from MS_WORK where work_id=? for update"));
    String queue = "select * from MS_WORK order by next_at_ms for update skip locked";
    assertEquals(queue, NowaitSqlProbe.point(queue));
  }
  @Test void dmlPrototypeIsExplicitAndLimitedToOwnedFixtureTable() {
    String update = "update sample_customer set display_name=? where storage_key=?";
    assertEquals(update, NowaitSqlProbe.dml(update, false));
    assertEquals(update + " nowait", NowaitSqlProbe.dml(update, true));
    String custom = "update CUSTOM_TABLE set value=?";
    assertEquals(custom, NowaitSqlProbe.dml(custom, true));
  }
  @Test void sqlGuardCannotExecuteAnUnboundedJpaLock() {
    var probe = new NowaitSqlProbe();
    assertThrows(IllegalStateException.class, () -> probe.inspect("select id from sample_customer for update"));
    assertEquals("select id from sample_customer for update nowait",
        probe.inspect("select id from sample_customer for update nowait"));
  }
  @Test void databaseRefusalIsNotAnArbitraryTimeoutOrCancellation() {
    assertTrue(OracleNowaitDiagnostic.oracle54(new java.sql.SQLException("synthetic", "72000", 54)));
    assertFalse(OracleNowaitDiagnostic.oracle54(new java.sql.SQLException("synthetic", "72000", 1013)));
    assertFalse(OracleNowaitDiagnostic.oracle54(new java.sql.SQLRecoverableException("synthetic", "08006", 18730)));
  }
}
