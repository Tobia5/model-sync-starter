package io.modelsync.jdbc;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** H2 integration lane. These tests do NOT qualify Oracle locking or migration behavior. */
class JdbcTriggerStoreTest {
  JdbcTemplate jdbc;
  JdbcWorkStore store;
  TransactionTemplate tx;
  final Instant now = Instant.parse("2026-01-01T00:00:00Z");
  final WorkKey key = new WorkKey("customer", new SubjectKey("tenant-a", "42"));

  @BeforeEach void setup() {
    var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
    ds.setUrl(ds.getUrl() + ";DB_CLOSE_DELAY=-1");
    new ResourceDatabasePopulator(new ClassPathResource("model-sync/schema/h2-v2.sql")).execute(ds);
    jdbc = new JdbcTemplate(ds);
    store = new JdbcWorkStore(jdbc);
    tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    store.verifySchema();
  }
  private WorkLease claim() {
    return tx.execute(s -> store.claim(Priority.HIGH, now, Duration.ofSeconds(60)).orElseThrow());
  }
  private void enqueue() {
    tx.executeWithoutResult(s -> store.enqueue(key, Priority.HIGH, false, now));
  }
  private void finish(WorkLease lease) {
    tx.executeWithoutResult(s -> {
      assertTrue(store.lockValid(lease, now));
      store.completeOnDemand(lease, new SyncState(key, now, now, "digest", "etag", "MATCH", null), now);
    });
  }
  private String status() {
    return jdbc.queryForObject("select work_status from MS_WORK where work_id=?", String.class, key.storageId());
  }

  @Test void v2SupportsIdleAndDoesNotPollIdleSubjects() {
    assertTrue(store.supportsOnDemand());
    enqueue(); finish(claim());
    assertEquals("IDLE", status());
    for (Priority p : Priority.values())
      assertTrue(tx.execute(s -> store.claim(p, now.plusSeconds(86400), Duration.ofSeconds(60))).isEmpty());
  }
  @Test void passiveVisitReactivatesIdleWithoutLosingEvidence() {
    enqueue(); finish(claim());
    tx.executeWithoutResult(s -> assertTrue(store.enqueueRefresh(key, Priority.HIGH, now.plusSeconds(61)).isPresent()));
    assertEquals("WAITING", status());
    assertEquals(now.toEpochMilli(), jdbc.queryForObject("select validated_at_ms from MS_STATE", Long.class));
  }
  @Test void visitsDoNotBumpGenerationDuringLease() {
    enqueue(); var lease = claim();
    tx.executeWithoutResult(s -> {
      for (int n = 0; n < 100; n++) store.enqueueRefresh(key, Priority.URGENT, now);
    });
    finish(lease);
    assertEquals("IDLE", status());
    assertEquals(lease.generation(), jdbc.queryForObject("select generation from MS_WORK", Long.class));
  }
  @Test void actualEventDuringLeaseSurvivesOnDemandCompletion() {
    enqueue(); var lease = claim();
    tx.executeWithoutResult(s -> store.enqueue(key, Priority.URGENT, true, now));
    finish(lease);
    assertEquals("WAITING", status());
    var next = tx.execute(s -> store.claim(Priority.URGENT, now, Duration.ofSeconds(60)).orElseThrow());
    assertTrue(next.force());
    assertEquals(lease.generation() + 1, next.generation());
  }
  @Test void visitPreservesRetryDeadlineAndAttemptCount() {
    enqueue(); var lease = claim();
    tx.executeWithoutResult(s -> store.reschedule(lease, now.plusSeconds(30), "FETCH_FAILED", true, 8, now));
    tx.executeWithoutResult(s -> store.enqueueRefresh(key, Priority.URGENT, now.plusSeconds(1)));
    assertEquals(now.plusSeconds(30).toEpochMilli(), jdbc.queryForObject("select next_at_ms from MS_WORK", Long.class));
    assertEquals(1, jdbc.queryForObject("select attempts from MS_WORK", Integer.class));
  }
  @Test void visitCannotReviveDeadWorkButExplicitRequestCan() {
    enqueue(); var lease = claim();
    tx.executeWithoutResult(s -> store.reschedule(lease, now, "INVALID_OWNER", true, 1, now));
    assertEquals("DEAD", status());
    assertTrue(tx.execute(s -> store.enqueueRefresh(key, Priority.URGENT, now)).isEmpty());
    assertEquals("DEAD", status());
    tx.executeWithoutResult(s -> store.enqueue(key, Priority.HIGH, true, now));
    assertEquals("WAITING", status());
  }
  @Test void completionAndStateRollBackTogether() {
    enqueue(); var lease = claim();
    assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
      store.completeOnDemand(lease, new SyncState(key, now, now, "digest", null, "MATCH", null), now);
      throw new IllegalStateException("injected");
    }));
    assertEquals("LEASED", status());
    assertEquals(0, jdbc.queryForObject("select count(*) from MS_STATE", Integer.class));
  }
  @Test void claimedOldTokenCannotChangeReclaimedSubject() {
    enqueue(); var old = claim();
    tx.executeWithoutResult(s -> store.claim(Priority.HIGH, now.plusSeconds(61), Duration.ofSeconds(60)).orElseThrow());
    assertThrows(LeaseLost.class, () -> tx.executeWithoutResult(s ->
        store.completeOnDemand(old, SyncState.empty(key), now.plusSeconds(61))));
  }
  @Test void v1StillValidButDoesNotSupportOnDemand() {
    jdbc.update("update MS_META set meta_value='1' where meta_name='schema_version'");
    store.verifySchema();
    assertFalse(store.supportsOnDemand());
    enqueue(); var lease = claim();
    assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> store.completeOnDemand(lease, SyncState.empty(key), now)));
  }
}
