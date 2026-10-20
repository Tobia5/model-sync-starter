package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcWorkStoreTest {
  JdbcTemplate jdbc;
  JdbcWorkStore store;
  TransactionTemplate tx;
  DataSourceTransactionManager manager;
  final Instant now = Instant.parse("2026-01-01T00:00:00Z");
  WorkKey key = new WorkKey("test", new SubjectKey("tenant", "42"));

  @BeforeEach
  void setup() {
    var ds =
        new DriverManagerDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource("model-sync/schema/h2-v1.sql")).execute(ds);
    jdbc = new JdbcTemplate(ds);
    manager = new DataSourceTransactionManager(ds);
    tx = new TransactionTemplate(manager);
    store = new JdbcWorkStore(jdbc);
    store.verifySchema();
  }

  @Test
  void enqueueRollbackIsAtomic() {
    assertThrows(
        IllegalStateException.class,
        () ->
            tx.execute(
                s -> {
                  store.enqueue(key, Priority.NORMAL, false, now);
                  throw new IllegalStateException();
                }));
    assertEquals(0, jdbc.queryForObject("select count(*) from MS_WORK", Integer.class));
  }

  @Test
  void duplicateRequestsCoalesceAndPromote() {
    tx.executeWithoutResult(
        s -> {
          store.enqueue(key, Priority.BACKGROUND, false, now);
          store.enqueue(key, Priority.URGENT, true, now);
        });
    var lease =
        tx.execute(s -> store.claim(Priority.URGENT, now, Duration.ofSeconds(60)).orElseThrow());
    assertNotNull(lease);
    assertEquals(2, lease.generation());
    assertTrue(lease.force());
  }

  @Test
  void expiredLeaseCannotCompleteAfterReclaim() {
    tx.executeWithoutResult(s -> store.enqueue(key, Priority.NORMAL, false, now));
    var first =
        tx.execute(s -> store.claim(Priority.NORMAL, now, Duration.ofSeconds(60)).orElseThrow());
    var second =
        tx.execute(
            s ->
                store
                    .claim(Priority.NORMAL, now.plusSeconds(61), Duration.ofSeconds(60))
                    .orElseThrow());
    assertNotEquals(first.token(), second.token());
    tx.executeWithoutResult(
        s -> {
          assertFalse(store.lockValid(first, now.plusSeconds(61)));
          assertTrue(store.lockValid(second, now.plusSeconds(61)));
        });
  }

  @Test
  void requestArrivingDuringLeaseRemainsPending() {
    tx.executeWithoutResult(s -> store.enqueue(key, Priority.NORMAL, false, now));
    var lease =
        tx.execute(s -> store.claim(Priority.NORMAL, now, Duration.ofSeconds(60)).orElseThrow());
    tx.executeWithoutResult(s -> store.enqueue(key, Priority.URGENT, true, now));
    tx.executeWithoutResult(
        s -> {
          assertTrue(store.lockValid(lease, now));
          store.complete(
              lease,
              new SyncState(key, now, now, "digest", "etag", "MATCH", null),
              now.plusSeconds(100),
              now);
        });
    var pending =
        tx.execute(s -> store.claim(Priority.URGENT, now, Duration.ofSeconds(60)).orElseThrow());
    assertTrue(pending.force());
    assertEquals(2, pending.generation());
  }

  @Test
  void twoGateInstancesShareTheDatabaseBudget() {
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    var first = new JdbcQuotaGate(jdbc, manager, clock);
    var second = new JdbcQuotaGate(jdbc, manager, clock);
    first.register("provider", Duration.ofSeconds(1));
    assertTrue(first.acquire("provider").granted());
    assertFalse(second.acquire("provider").granted());
  }

  @Test
  void persistedQuotaMismatchFailsClosed() {
    var gate = new JdbcQuotaGate(jdbc, manager, Clock.fixed(now, ZoneOffset.UTC));
    gate.register("provider", Duration.ofSeconds(1));
    assertThrows(
        IllegalStateException.class, () -> gate.register("provider", Duration.ofSeconds(2)));
  }
}
