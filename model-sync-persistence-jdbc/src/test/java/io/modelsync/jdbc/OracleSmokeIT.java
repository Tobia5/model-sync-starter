package io.modelsync.jdbc;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in Oracle smoke test. Requires a pre-provisioned, DEDICATED schema; never applies DDL. */
class OracleSmokeIT {
  @Test
  void claimAndFencingOnOracle() {
    assertEquals(
        "DEDICATED_TEST_SCHEMA",
        System.getenv("MODELSYNC_ORACLE_TEST_SCHEMA_ACK"),
        "Explicit test-schema acknowledgment required");
    String url = System.getenv("MODELSYNC_ORACLE_URL"),
        user = System.getenv("MODELSYNC_ORACLE_USER"),
        password = System.getenv("MODELSYNC_ORACLE_PASSWORD");
    assertNotNull(url);
    assertNotNull(user);
    assertNotNull(password);
    var ds = new DriverManagerDataSource(url, user, password);
    ds.setDriverClassName("oracle.jdbc.OracleDriver");
    var jdbc = new JdbcTemplate(ds);
    var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    assertEquals(
        0L,
        jdbc.queryForObject("select count(*) from MS_WORK", Long.class),
        "Use an empty dedicated ledger schema");
    var store = OracleQualificationStores.work(jdbc);
    store.verifySchema();
    var clock = new DatabaseClock(jdbc);
    WorkKey key = new WorkKey("oracle-it-" + UUID.randomUUID(), new SubjectKey("test", "1"));
    try {
      Instant now = clock.instant();
      tx.executeWithoutResult(s -> store.enqueue(key, Priority.URGENT, true, now));
      WorkLease lease =
          tx.execute(s -> store.claim(Priority.URGENT, now, Duration.ofSeconds(60)).orElseThrow());
      assertNotNull(lease);
      assertEquals(key, lease.key());
      tx.executeWithoutResult(
          s -> {
            assertTrue(store.lockValid(lease, clock.instant()));
            store.complete(
                lease,
                new SyncState(key, now, now, "digest", null, "MATCH", null),
                now.plusSeconds(60),
                now);
          });
      tx.executeWithoutResult(s -> assertFalse(store.lockValid(lease, clock.instant())));
    } finally {
      // Remove only the exact subject created by this test, not any other application data.
      jdbc.update("delete from MS_STATE where work_id=?", key.storageId());
      jdbc.update("delete from MS_WORK where work_id=?", key.storageId());
    }
  }
}
