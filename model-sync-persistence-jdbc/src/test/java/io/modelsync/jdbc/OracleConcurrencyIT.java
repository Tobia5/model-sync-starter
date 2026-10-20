package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.Priority;
import io.modelsync.core.WorkLease;
import io.modelsync.core.domain.SubjectKey;
import io.modelsync.core.domain.WorkKey;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Disposable Oracle checks for competing connections and lease fencing; no DDL is applied. */
class OracleConcurrencyIT {
  private JdbcTemplate jdbc;
  private JdbcWorkStore store;
  private TransactionTemplate tx;
  private WorkKey key;

  @BeforeEach void setup() {
    assertEquals("DEDICATED_TEST_SCHEMA", System.getenv("MODELSYNC_ORACLE_TEST_SCHEMA_ACK"));
    String url=System.getenv("MODELSYNC_ORACLE_URL");
    String user=System.getenv("MODELSYNC_ORACLE_USER");
    String password=System.getenv("MODELSYNC_ORACLE_PASSWORD");
    assertNotNull(url); assertNotNull(user); assertNotNull(password);
    var ds=new DriverManagerDataSource(url,user,password);
    ds.setDriverClassName("oracle.jdbc.OracleDriver");
    jdbc=new JdbcTemplate(ds); store=new JdbcWorkStore(jdbc);
    tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
    store.verifySchema();
    key=new WorkKey("oracle-concurrency-"+UUID.randomUUID(),new SubjectKey("synthetic", "1"));
  }

  @AfterEach void cleanup() {
    if (jdbc != null && key != null) {
      jdbc.update("delete from MS_STATE where work_id=?",key.storageId());
      jdbc.update("delete from MS_WORK where work_id=?",key.storageId());
    }
  }

  @Test void competingConnectionsCoalesceOneLogicalWorkItem() throws Exception {
    Instant now=Instant.now();
    CountDownLatch start=new CountDownLatch(1);
    try(var workers=Executors.newFixedThreadPool(2)) {
      var first=workers.submit(() -> {start.await();return tx.execute(s -> store.enqueue(key,Priority.HIGH,false,now));});
      var second=workers.submit(() -> {start.await();return tx.execute(s -> store.enqueue(key,Priority.URGENT,true,now));});
      start.countDown();
      assertEquals(key.storageId(),first.get(15,TimeUnit.SECONDS));
      assertEquals(key.storageId(),second.get(15,TimeUnit.SECONDS));
    }
    assertEquals(1,jdbc.queryForObject("select count(*) from MS_WORK where work_id=?",Integer.class,key.storageId()));
  }

  @Test void reclaimedLeaseRejectsOldToken() {
    Instant now=Instant.now();
    tx.executeWithoutResult(s -> store.enqueue(key,Priority.URGENT,true,now));
    WorkLease old=tx.execute(s -> store.claim(Priority.URGENT,now,Duration.ofSeconds(30)).orElseThrow());
    WorkLease current=tx.execute(s -> store.claim(Priority.URGENT,now.plusSeconds(31),Duration.ofSeconds(30)).orElseThrow());
    assertNotEquals(old.token(),current.token());
    tx.executeWithoutResult(s -> {
      assertFalse(store.lockValid(old,now.plusSeconds(31)));
      assertTrue(store.lockValid(current,now.plusSeconds(31)));
    });
  }
}
