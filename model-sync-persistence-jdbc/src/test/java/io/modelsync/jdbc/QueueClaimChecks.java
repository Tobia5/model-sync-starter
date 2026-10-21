package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.*;
import io.modelsync.core.domain.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The same locking/fairness regressions run cheaply on H2 and explicitly on real Oracle. */
abstract class QueueClaimChecks {
  protected JdbcTemplate jdbc;
  protected JdbcWorkStore store;
  protected TransactionTemplate tx;
  private DataSource source;
  private final String binding = "queue-check-" + UUID.randomUUID();
  private final Instant now = Instant.parse("2026-10-01T00:00:00Z");
  protected abstract DataSource source();

  @BeforeEach void setupQueue() {
    source = source();
    jdbc = new JdbcTemplate(source);
    tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    store = new JdbcWorkStore(jdbc);
  }

  @AfterEach void cleanupQueue() {
    jdbc.update("delete from MS_STATE where work_id in (select work_id from MS_WORK where binding_id=?)", binding);
    jdbc.update("delete from MS_WORK where binding_id in (?,?)", binding,binding+"-other");
  }

  private WorkKey enqueue(String id, Priority priority, Instant due) {
    var key = new WorkKey(binding, new SubjectKey("synthetic", id));
    tx.executeWithoutResult(s -> store.enqueue(key, priority, true, due));
    return key;
  }

  private Optional<WorkLease> claim(Priority priority) {
    return tx.execute(s -> store.claim(priority, now, Duration.ofSeconds(30)));
  }

  @Test void emptyAndFutureQueuesDoNotLease() {
    assertTrue(claim(Priority.NORMAL).isEmpty());
    enqueue("future", Priority.NORMAL, now.plusSeconds(60));
    assertTrue(claim(Priority.NORMAL).isEmpty());
  }

  @Test void waitingOrderAndFutureBoundaryArePreserved() {
    enqueue("future", Priority.NORMAL, now.plusMillis(1));
    var later = enqueue("later", Priority.NORMAL, now);
    var earliest = enqueue("earliest", Priority.NORMAL, now.minusSeconds(1));
    assertEquals(earliest, claim(Priority.NORMAL).orElseThrow().key());
    assertEquals(later, claim(Priority.NORMAL).orElseThrow().key());
    assertTrue(claim(Priority.NORMAL).isEmpty());
  }

  @Test void lockedEarliestDoesNotHideLaterUnlockedWaiting() throws Exception {
    var earliest = enqueue("earliest", Priority.NORMAL, now.minusSeconds(1));
    var later = enqueue("later", Priority.NORMAL, now);
    try (var connection = source.getConnection()) {
      connection.setAutoCommit(false);
      try (var statement = connection.prepareStatement("select work_id from MS_WORK where work_id=? for update")) {
        statement.setString(1, earliest.storageId());
        try (var rows = statement.executeQuery()) { assertTrue(rows.next()); }
        assertEquals(later, claim(Priority.NORMAL).orElseThrow().key());
      } finally { connection.rollback(); }
    }
    assertEquals(earliest, claim(Priority.NORMAL).orElseThrow().key());
  }

  @Test void simultaneousWorkersNeverClaimTheSameLease() throws Exception {
    for (int i = 0; i < 24; i++) enqueue("parallel-" + i, Priority.HIGH, now);
    var start = new CountDownLatch(1);
    Set<WorkKey> claimed = ConcurrentHashMap.newKeySet();
    try (var workers = Executors.newFixedThreadPool(4)) {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < 4; i++) futures.add(workers.submit(() -> {
        start.await();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (claimed.size() < 24 && System.nanoTime() < deadline) {
          var result = claim(Priority.HIGH);
          if (result.isEmpty()) { Thread.sleep(1); continue; }
          var lease = result.orElseThrow();
          assertTrue(claimed.add(lease.key()), "Duplicate concurrent logical claim");
          tx.executeWithoutResult(s -> assertTrue(store.lockValid(lease, now)));
        }
        return null;
      }));
      start.countDown();
      for (var future : futures) future.get(30, TimeUnit.SECONDS);
    }
    assertEquals(24, claimed.size());
  }

  @Test void recoveryAndWaitingBothProgressUnderRepeatedExpiry() {
    var expired = enqueue("expired", Priority.NORMAL, now.minusSeconds(60));
    jdbc.update("update MS_WORK set work_status='LEASED',lease_token='old',lease_until_ms=?,leased_generation=generation where work_id=?",
        now.minusMillis(1).toEpochMilli(), expired.storageId());
    for (int i = 0; i < 8; i++) enqueue("waiting-" + i, Priority.NORMAL, now);
    int recovered = 0;
    int ordinary = 0;
    String previous = "old";
    for (int i = 0; i < 8; i++) {
      var lease = claim(Priority.NORMAL).orElseThrow();
      if (lease.key().equals(expired)) {
        recovered++;
        assertNotEquals(previous, lease.token());
        previous = lease.token();
        jdbc.update("update MS_WORK set lease_until_ms=? where work_id=?", now.minusMillis(1).toEpochMilli(), expired.storageId());
      } else ordinary++;
    }
    assertEquals(4, recovered);
    assertEquals(4, ordinary);
  }

  @Test void lockedEarliestRecoverySkipsToLaterExpiredLease() throws Exception {
    var first = enqueue("first", Priority.NORMAL, now);
    var second = enqueue("second", Priority.NORMAL, now);
    jdbc.update("update MS_WORK set work_status='LEASED',lease_token='old',lease_until_ms=?,leased_generation=generation where binding_id=?",
        now.minusMillis(1).toEpochMilli(), binding);
    jdbc.update("update MS_WORK set lease_until_ms=? where work_id=?", now.minusSeconds(5).toEpochMilli(), first.storageId());
    try (var connection = source.getConnection()) {
      connection.setAutoCommit(false);
      try (var statement = connection.prepareStatement("select work_id from MS_WORK where work_id=? for update")) {
        statement.setString(1, first.storageId());
        try (var rows = statement.executeQuery()) { assertTrue(rows.next()); }
        assertEquals(second, claim(Priority.NORMAL).orElseThrow().key());
      } finally { connection.rollback(); }
    }
  }

  @Test void activeLeasesAndOtherPrioritiesAreNotClaimed() {
    enqueue("urgent", Priority.URGENT, now);
    enqueue("background", Priority.BACKGROUND, now);
    var normal = enqueue("normal", Priority.NORMAL, now);
    jdbc.update("update MS_WORK set work_status='LEASED',lease_token='active',lease_until_ms=?,leased_generation=generation where work_id=?",
        now.plusSeconds(1).toEpochMilli(), normal.storageId());
    assertTrue(claim(Priority.NORMAL).isEmpty());
    assertEquals(Priority.URGENT, claim(Priority.URGENT).orElseThrow().priority());
    assertEquals(Priority.BACKGROUND, claim(Priority.BACKGROUND).orElseThrow().priority());
  }
  @Test void lockedPrefixDoesNotHideWorkBeyondDriverFetch() throws Exception {
    for (int i = 0; i < 20; i++) enqueue("prefix-" + i, Priority.NORMAL, now.minusSeconds(2));
    var later = enqueue("unlocked-later", Priority.NORMAL, now);
    try (var connection = source.getConnection()) {
      connection.setAutoCommit(false);
      try (var statement = connection.prepareStatement("select work_id from MS_WORK where binding_id=? and subject_id like 'prefix-%' for update")) {
        statement.setString(1, binding);
        int locked = 0;
        try (var rows = statement.executeQuery()) { while (rows.next()) locked++; }
        assertEquals(20, locked);
        assertEquals(later, claim(Priority.NORMAL).orElseThrow().key());
      } finally { connection.rollback(); }
    }
  }
  @Test void filteringWaitingWorkNeverSuppressesRecovery() {
    var expired = enqueue("expired-filtered", Priority.NORMAL, now);
    enqueue("waiting-filtered", Priority.NORMAL, now);
    jdbc.update("update MS_WORK set work_status='LEASED',lease_until_ms=?,lease_token='old',leased_generation=generation where work_id=?",now.minusMillis(1).toEpochMilli(),expired.storageId());
    var lease = tx.execute(s -> store.claim(Priority.NORMAL,now,Duration.ofSeconds(30),Set.of())).orElseThrow();
    assertEquals(expired,lease.key());
    assertTrue(tx.execute(s -> store.claim(Priority.NORMAL,now,Duration.ofSeconds(30),Set.of())).isEmpty());
    assertEquals(1,jdbc.queryForObject("select count(*) from MS_WORK where binding_id=? and work_status='WAITING'",Integer.class,binding));
  }

  @Test void availableBindingProgressesPastDelayedEarlierWork() {
    enqueue("delayed-first",Priority.NORMAL,now.minusSeconds(1));
    var other = new WorkKey(binding+"-other",new SubjectKey("synthetic","available"));
    tx.executeWithoutResult(s -> store.enqueue(other,Priority.NORMAL,true,now));
    assertEquals(other,tx.execute(s -> store.claim(Priority.NORMAL,now,Duration.ofSeconds(30),Set.of(other.binding()))).orElseThrow().key());
  }

  @Test void availableBindingsAlternateWithinOnePriority() {
    for(int i=0;i<4;i++) {
      enqueue("first-"+i,Priority.NORMAL,now.minusSeconds(1));
      var other=new WorkKey(binding+"-other",new SubjectKey("synthetic","other-"+i));
      tx.executeWithoutResult(s -> store.enqueue(other,Priority.NORMAL,true,now));
    }
    Map<String,Integer> counts=new HashMap<>();
    for(int i=0;i<4;i++) {
      var lease=tx.execute(s -> store.claim(Priority.NORMAL,now,Duration.ofSeconds(30),Set.of(binding,binding+"-other"))).orElseThrow();
      counts.merge(lease.key().binding(),1,Integer::sum);
    }
    assertEquals(Map.of(binding,2,binding+"-other",2),counts);
  }
}
