package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariDataSource;
import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/** Actual process death; exceptions and logical cancellation are not crash evidence. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OracleRecoveryIT {
  private HikariDataSource setup;
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private SyncCoordinator coordinator;
  private SyncGateway gateway;
  private LocalTransactions tx;
  private WorkStore store;
  private ModelBinding<Customer> binding;
  private Map<String, Object> report;
  private final List<Map<String, Object>> cases = new ArrayList<>();
  private final List<String> ownedIds = new ArrayList<>();
  private boolean tableOwned;

  @BeforeAll void open() throws Exception {
    report = FaultEvidence.report("independent-jvm-oracle-jpa-crash-recovery"); report.put("cases", cases);
    report.put("lease_ms", 5000); report.put("recovery_budget_ms", 30000);
    setup = RecoveryConfiguration.source(System.getenv("MODELSYNC_ORACLE_URL"), 2);
    jdbc = new JdbcTemplate(setup); jdbc.setQueryTimeout(2);
    assertEquals(0, jdbc.queryForObject("select count(*) from user_tables where table_name='SAMPLE_CUSTOMER'", Integer.class));
    new ResourceDatabasePopulator(new ClassPathResource("sample-oracle.sql")).execute(setup); tableOwned = true;
    context = RecoveryProcess.open("--modelsync.recovery.directory=" + FaultEvidence.root().resolve("model-sync-sample/target/qm/recovery-parent"));
    var source = context.getBean(HikariDataSource.class);
    assertSame(source, ((JpaTransactionManager) context.getBean(PlatformTransactionManager.class)).getDataSource());
    coordinator = context.getBean(SyncCoordinator.class); gateway = context.getBean(SyncGateway.class);
    tx = context.getBean(LocalTransactions.class); store = context.getBean(WorkStore.class);
    @SuppressWarnings("unchecked") var actualBinding = (ModelBinding<Customer>) context.getBean(ModelBinding.class);
    binding = actualBinding;
  }

  @Test void crashAfterDurableClaim() throws Exception { crash(RecoveryPoint.CLAIM_COMMITTED, false, false); }
  @Test void crashAfterOwnerResponse() throws Exception { crash(RecoveryPoint.OWNER_RESPONDED, false, false); }
  @Test void crashAfterRealProjectionFlush() throws Exception { crash(RecoveryPoint.PROJECTION_FLUSHED, false, false); }
  @Test void crashAfterCommitBeforeObservedSuccess() throws Exception { crash(RecoveryPoint.COMPLETION_COMMITTED, false, false); }
  @Test void newerRequestSurvivesCrashAndOldToken() throws Exception { crash(RecoveryPoint.OWNER_RESPONDED, true, false); }
  @Test void localRevisionAndOwnedFieldsSurviveCrash() throws Exception { crash(RecoveryPoint.OWNER_RESPONDED, true, true); }

  private void crash(RecoveryPoint point, boolean newer, boolean localChange) throws Exception {
    String name = point + (localChange ? "-local-revision" : newer ? "-new-generation" : "");
    Map<String, Object> result = new LinkedHashMap<>(); cases.add(result); result.put("name", name); result.put("status", "FAILED");
    SubjectKey subject = new SubjectKey("synthetic-crash", UUID.randomUUID().toString());
    WorkKey key = new WorkKey("customer", subject); String id = key.storageId(); ownedIds.add(id);
    gateway.request("customer", subject.tenant(), subject.id(), Priority.URGENT, true);
    assertTrue(coordinator.runOne());
    assertEquals("before", snapshot(id).name());
    Projection<Customer> local = tx.required(() -> binding.projection().load(subject).orElseThrow());
    SyncState previous = tx.required(() -> store.state(key));
    gateway.request("customer", subject.tenant(), subject.id(), Priority.URGENT, true);
    Path directory = FaultEvidence.root().resolve("model-sync-sample/target/qm/recovery-" + report.get("run_id") + "-" + name);
    long began = System.nanoTime();
    try (var child = new OwnedRecoveryJvm(directory, point, "after")) {
      await(() -> Files.isRegularFile(directory.resolve("checkpoint.json")) || !child.process.isAlive(), 30000);
      assertTrue(child.process.isAlive(), "Child exited before acknowledged checkpoint; see its bounded local log");
      var checkpoint = FaultEvidence.read(directory.resolve("checkpoint.json"));
      assertEquals(point.name(), checkpoint.get("point"));
      assertEquals(child.process.pid(), ((Number) checkpoint.get("pid")).longValue());
      assertEquals(id, checkpoint.get("work_id"), "The acknowledged claim must belong to this exact synthetic case");
      var oldLease = new WorkLease(key, (String) checkpoint.get("token"), ((Number) checkpoint.get("generation")).longValue(),
          0, true, Priority.URGENT, Instant.ofEpochMilli(((Number) checkpoint.get("lease_until_ms")).longValue()));
      result.put("checkpoint", checkpoint);
      if (newer) gateway.request("customer", subject.tenant(), subject.id(), Priority.URGENT, true);
      if (localChange) jdbc.update("update SAMPLE_CUSTOMER set display_name='local-edit',local_version=local_version+1,"
          + "review_requested_at=systimestamp where storage_key=?", id);
      long kill = System.nanoTime(); child.kill();
      result.put("kill_ms", millis(kill)); result.put("child_exit", child.process.exitValue()); result.put("child_absent", !child.process.isAlive());
      RecoverySnapshot crashed = snapshot(id); result.put("after_crash", crashed);
      boolean committed = point == RecoveryPoint.COMPLETION_COMMITTED;
      assertEquals(committed ? "after" : localChange ? "local-edit" : "before", crashed.name());
      assertEquals(committed ? "after" : "before", crashed.validator());
      assertEquals("APPLIED", crashed.outcome());
      assertEquals(newer ? 3 : 2, crashed.generation());
      assertEquals(committed ? "IDLE" : "LEASED", crashed.status());
      long recoveredAt = System.nanoTime();
      if (!committed) {
        await(() -> System.currentTimeMillis() > oldLease.expiresAt().toEpochMilli(), 7000);
        try (var recovery = new OwnedRecoveryJvm(directory.resolve("fresh-process"), RecoveryPoint.NONE, "recovered")) {
          assertTrue(recovery.process.waitFor(30, TimeUnit.SECONDS), "Fresh recovery process exceeded bound");
          assertEquals(0, recovery.process.exitValue(), "See fresh process log");
          result.put("recovery_process_exit", recovery.process.exitValue());
        }
      }
      result.put("recovery_ms", millis(recoveredAt));
      var recovered = snapshot(id); result.put("after_recovery", recovered);
      assertEquals("IDLE", recovered.status()); assertNull(recovered.token());
      assertEquals(committed ? "after" : "recovered", recovered.name());
      assertEquals(recovered.name(), recovered.validator());
      assertEquals(crashed.generation(), recovered.generation());
      assertTrue(recovered.version() >= crashed.version());
      assertEquals(localChange, recovered.locallyMarked());
      var obsolete = new PreparedSync<>(oldLease, binding, local, previous, new ReadRequest(subject, previous.validator()));
      assertFalse(tx.required(() -> store.lockValid(oldLease, Instant.now())));
      assertThrows(LeaseLost.class, () -> coordinator.finish(obsolete, FetchResult.found(new Customer("obsolete", "ACTIVE"), "obsolete")));
      assertEquals(recovered, snapshot(id));
      long firstReads = reads(directory); long recoveredReads = reads(directory.resolve("fresh-process"));
      assertEquals(point == RecoveryPoint.CLAIM_COMMITTED ? 0 : 1, firstReads);
      assertEquals(committed ? 0 : 1, recoveredReads);
      result.put("crashed_owner_reads", firstReads); result.put("recovery_owner_reads", recoveredReads);
      result.put("persisted_successful_subjects", 1); result.put("successful_recovery_applications", committed ? 0 : 1);
      result.put("obsolete_completion_rejected", true); result.put("elapsed_ms", millis(began)); result.put("status", "PASS");
    } finally { FaultEvidence.write("oracle-recovery.json", report); }
  }

  private long reads(Path directory) throws Exception {
    Path file = directory.resolve("owner-reads.log"); return Files.exists(file) ? Files.readAllLines(file).size() : 0;
  }
  private RecoverySnapshot snapshot(String id) throws Exception {
    try (var connection = setup.getConnection()) {
      connection.setAutoCommit(false);
      try (var query = connection.prepareStatement("select p.display_name,p.local_version,s.owner_validator,s.outcome,"
          + "w.work_status,w.lease_token,w.generation,w.lease_until_ms,p.review_requested_at from SAMPLE_CUSTOMER p"
          + " join MS_WORK w on w.work_id=p.storage_key join MS_STATE s on s.work_id=w.work_id where w.work_id=?")) {
        query.setQueryTimeout(2); query.setString(1, id);
        try (var row = query.executeQuery()) {
          assertTrue(row.next()); var value = new RecoverySnapshot(row.getString(1), row.getLong(2), row.getString(3),
              row.getString(4), row.getString(5), row.getString(6), row.getLong(7), row.getLong(8), row.getObject(9) != null);
          assertFalse(row.next()); connection.rollback(); return value;
        }
      }
    }
  }
  static long millis(long began) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began); }
  static void await(BooleanSupplier condition, long milliseconds) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds);
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
    assertTrue(condition.getAsBoolean(), "Acknowledged condition exceeded " + milliseconds + "ms");
  }
  @AfterEach void removeOnlyThisCasesWork() {
    if (jdbc != null) for (String id : ownedIds) {
      jdbc.update("delete from MS_STATE where work_id=?", id);
      jdbc.update("delete from MS_WORK where work_id=?", id);
      jdbc.update("delete from SAMPLE_CUSTOMER where storage_key=?", id);
    }
    ownedIds.clear();
  }
  @AfterAll void close() throws Exception {
    if (context != null) {
      String poolName = context.getBean(HikariDataSource.class).getPoolName();
      context.close(); FaultEvidence.awaitNoPoolThreads(poolName);
    }
    if (setup != null) {
      if (tableOwned) {
        for (String id : ownedIds) { jdbc.update("delete from MS_STATE where work_id=?", id); jdbc.update("delete from MS_WORK where work_id=?", id); }
        jdbc.execute("drop table SAMPLE_CUSTOMER purge");
        jdbc.update("delete from MS_QUOTA where quota_group='recovery-owner'");
      }
      assertEquals(0, setup.getHikariPoolMXBean().getActiveConnections()); setup.close();
      FaultEvidence.awaitNoPoolThreads(setup.getPoolName());
    }
    if (report != null) {
      report.put("status", cases.size() == 6 && cases.stream().allMatch(c -> "PASS".equals(c.get("status"))) ? "PASS" : "FAILED");
      report.put("owned_pool_closed", setup != null && setup.isClosed()); FaultEvidence.write("oracle-recovery.json", report);
    }
  }
}
