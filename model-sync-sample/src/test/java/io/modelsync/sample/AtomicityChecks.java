package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.LocalTransactions;
import io.modelsync.core.port.WorkStore;
import io.modelsync.jdbc.JdbcWorkStore;
import io.modelsync.spring.SpringLocalTransactions;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Identical cheap/Oracle assertions; Oracle adds a failure in the database's COMMIT itself. */
abstract class AtomicityChecks {
  @Autowired SyncGateway gateway;
  @Autowired SyncCoordinator coordinator;
  @Autowired WorkStore store;
  @Autowired LocalTransactions tx;
  @Autowired PlatformTransactionManager manager;
  @Autowired EntityManagerFactory emf;
  @Autowired DataSource source;
  @Autowired AtomicityFaults faults;
  SubjectKey key;
  String id;
  JdbcTemplate jdbc;

  @BeforeEach void committedInitialReconciliation() throws Exception {
    assertInstanceOf(JpaTransactionManager.class, manager);
    assertSame(source, ((JpaTransactionManager) manager).getDataSource());
    assertSame(emf, ((JpaTransactionManager) manager).getEntityManagerFactory());
    assertInstanceOf(JdbcWorkStore.class, store);
    assertInstanceOf(SpringLocalTransactions.class, tx);
    faults.reset();
    jdbc = new JdbcTemplate(source);
    key = new SubjectKey("synthetic-atomicity", UUID.randomUUID().toString());
    id = new WorkKey("customer", key).storageId();
    jdbc.update("update MS_QUOTA set next_allowed_ms=0 where quota_group='atomicity-owner'");
    gateway.request("customer", key.tenant(), key.id(), Priority.NORMAL, true);
    assertTrue(coordinator.runOne());
    assertEquals("before", committed().name());
    assertEquals("before", committed().validator());
    assertEquals("APPLIED", committed().outcome());
  }

  @AfterEach void cleanupOwnSubject() {
    faults.reset();
    if (id != null && jdbc != null) {
      jdbc.update("delete from MS_STATE where work_id=?", id);
      jdbc.update("delete from MS_WORK where work_id=?", id);
      jdbc.update("delete from SAMPLE_CUSTOMER where storage_key=?", id);
    }
  }

  @Test void successfulProjectionAndLedgerCommitTogether() throws Exception {
    var prepared = prepare();
    coordinator.finish(prepared, FetchResult.found(new Customer("after", "ACTIVE"), "after"));
    var actual = committed();
    assertEquals("after", actual.name());
    assertEquals("after", actual.validator());
    assertEquals("APPLIED", actual.outcome());
    assertEquals("WAITING", actual.workStatus());
    assertNull(actual.token());
  }

  @Test void failureAfterProjectionFlushRollsBackBoth() throws Exception {
    failedCompletion(AtomicityFault.AFTER_FLUSH);
  }

  @Test void failureDuringJdbcLedgerWriteRollsBackJpaFlush() throws Exception {
    failedCompletion(AtomicityFault.LEDGER_WRITE);
  }

  @Test void failureAtSpringCompletionRollsBackBoth() throws Exception {
    failedCompletion(AtomicityFault.BEFORE_COMMIT);
    assertTrue(faults.completionReached);
    assertEquals(TransactionSynchronization.STATUS_ROLLED_BACK, faults.completionStatus);
  }

  @Test void changedLocalRevisionReschedulesWithoutOverwriting() throws Exception {
    var prepared = prepare();
    jdbc.update("update SAMPLE_CUSTOMER set display_name='local-change',local_version=local_version+1 where storage_key=?", id);
    coordinator.finish(prepared, FetchResult.found(new Customer("after", "ACTIVE"), "after"));
    var actual = committed();
    assertEquals("local-change", actual.name());
    assertEquals("before", actual.validator());
    assertEquals("WAITING", actual.workStatus());
    assertEquals("LOCAL_CONFLICT", jdbc.queryForObject("select last_error_code from MS_WORK where work_id=?", String.class, id));
  }

  @Test void obsoleteLeaseCannotApplyProjectionOrLedger() throws Exception {
    var prepared = prepare();
    jdbc.update("update MS_WORK set lease_until_ms=0 where work_id=?", id);
    var replacement = tx.required(() -> store.claim(Priority.NORMAL, Instant.now(), java.time.Duration.ofSeconds(60)).orElseThrow());
    assertNotEquals(prepared.lease().token(), replacement.token());
    assertThrows(LeaseLost.class, () -> coordinator.finish(prepared,
        FetchResult.found(new Customer("after", "ACTIVE"), "after")));
    assertEquals("before", committed().name());
    assertEquals("before", committed().validator());
    assertEquals(replacement.token(), committed().token());
  }

  PreparedSync<?> prepare() {
    faults.reset();
    jdbc.update("update MS_QUOTA set next_allowed_ms=0 where quota_group='atomicity-owner'");
    gateway.request("customer", key.tenant(), key.id(), Priority.NORMAL, true);
    var lease = coordinator.claim().orElseThrow();
    assertTrue(tx.required(() -> store.lockValid(lease, Instant.now())));
    return coordinator.prepare(lease).orElseThrow();
  }

  Exception failedCompletion(AtomicityFault fault) throws Exception {
    var prepared = prepare();
    var before = committed();
    faults.fault = fault;
    String validator = fault == AtomicityFault.LEDGER_WRITE ? "ledger-failure" : "after";
    Exception failure = assertThrows(Exception.class, () -> coordinator.finish(prepared,
        FetchResult.found(new Customer("after", "ACTIVE"), validator)));
    assertTrue(faults.projectionFlushed, "Failure must occur after the real projection flush");
    assertEquals(before, committed(), "No projection, evidence or successful ledger transition may commit");
    assertTrue(tx.required(() -> store.lockValid(prepared.lease(), Instant.now())));
    // Retry state intentionally persists in a separate transaction after the atomic rollback.
    faults.fault = AtomicityFault.NONE;
    coordinator.failed(prepared.lease(), failure);
    var retry = committed();
    assertEquals(before.name(), retry.name());
    assertEquals(before.version(), retry.version());
    assertEquals(before.validator(), retry.validator());
    assertEquals(before.outcome(), retry.outcome());
    assertEquals("WAITING", retry.workStatus());
    assertNull(retry.token());
    assertEquals(1, retry.attempts());
    assertEquals(before.generation(), retry.generation());
    return failure;
  }

  AtomicitySnapshot committed() throws Exception {
    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    // Bypass JPA's persistence context and Spring's thread-bound resource lookup entirely.
    try (Connection connection = source.getConnection()) {
      connection.setAutoCommit(false);
      try (var query = connection.prepareStatement("select p.display_name,p.local_version,s.owner_validator,s.outcome,"
          + "w.work_status,w.lease_token,w.generation,w.attempts from SAMPLE_CUSTOMER p join MS_WORK w"
          + " on w.work_id=p.storage_key join MS_STATE s on s.work_id=w.work_id where w.work_id=?")) {
        query.setString(1, id);
        try (var result = query.executeQuery()) {
          assertTrue(result.next());
          var actual = new AtomicitySnapshot(result.getString(1), result.getLong(2), result.getString(3),
              result.getString(4), result.getString(5), result.getString(6), result.getLong(7), result.getInt(8));
          assertFalse(result.next());
          connection.rollback();
          return actual;
        }
      }
    }
  }
}
