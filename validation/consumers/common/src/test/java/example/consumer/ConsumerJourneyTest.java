package example.consumer;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariDataSource;
import io.modelsync.autoconfigure.ModelSyncAutoConfiguration;
import io.modelsync.core.application.SyncGateway;
import io.modelsync.core.domain.SubjectKey;
import io.modelsync.core.domain.WorkKey;
import io.modelsync.core.port.QuotaGate;
import io.modelsync.core.port.WorkStore;
import io.modelsync.jdbc.*;
import io.modelsync.jpa.*;
import io.modelsync.reactor.ReactorSyncWorker;
import io.modelsync.reactor.WorkerShutdownStatus;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

class ConsumerJourneyTest {
  @Test void independentArtifactsStartReconcileReplaceAndDisable() throws Exception {
    assertEquals("DEDICATED_TEST_SCHEMA", System.getenv("MODELSYNC_ORACLE_TEST_SCHEMA_ACK"));
    assertEquals("DISPOSABLE_NONPRODUCTION", System.getenv("MODELSYNC_FULL_ENV_ACK"));
    var receipt = new LinkedHashMap<String, Object>();
    receipt.put("status", "FAILED"); receipt.put("pid", ProcessHandle.current().pid());
    receipt.put("run_id", System.getenv("CONSUMER_RUN_ID"));
    receipt.put("producer_source", System.getenv("CONSUMER_SOURCE_SHA"));
    receipt.put("producer_tree", System.getenv("CONSUMER_SOURCE_TREE"));
    receipt.put("consumer_manifest_sha256", System.getenv("CONSUMER_MANIFEST_SHA256"));
    String mode = System.getProperty("consumer.mode"); receipt.put("framework_mode", mode);
    var source = new DriverManagerDataSource(System.getenv("MODELSYNC_ORACLE_URL"),
        System.getenv("MODELSYNC_ORACLE_USER"), System.getenv("MODELSYNC_ORACLE_PASSWORD"));
    var observer = new JdbcTemplate(source);
    var key = new WorkKey("item", new SubjectKey("synthetic", "known-id"));
    List<Map<String, Object>> phases = new ArrayList<>(); receipt.put("committed_phases", phases);
    observer.execute("create table CONSUMER_ITEM (storage_key varchar2(64 char) primary key, "
        + "tenant_id varchar2(128 char) not null, source_id varchar2(256 char) not null, "
        + "local_version number(19) not null,item_value varchar2(80 char),expires_at timestamp(6) with time zone,"
        + "constraint CONSUMER_ITEM_UQ unique(tenant_id,source_id))");
    ConfigurableApplicationContext host = null; ReactorSyncWorker worker = null;
    HikariDataSource pool = null; ConsumerObservation observation = null;
    try (var owner = new LocalOwner(); var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
      try {
        host = start(mode, true, owner.url());
        receipt.put("context_class", host.getClass().getName());
        assertTrue(host.getClass().getName().contains("mvc".equals(mode) ? "Servlet" : "Reactive"));
        pool = host.getBean(HikariDataSource.class); observation = host.getBean(ConsumerObservation.class);
        worker = host.getBean(ReactorSyncWorker.class);
        inspect(host, receipt);
        int port = Integer.parseInt(host.getEnvironment().getRequiredProperty("local.server.port"));
        String endpoint = "http://127.0.0.1:" + port;
        assertFalse(post(http, endpoint + "/request").isBlank());
        awaitCommitted(observer, key.storageId(), "A", 0, 1, phases);
        assertEquals("A", get(http, endpoint + "/view"));
        owner.value.set("B"); observation.sql.clear(); observation.dropped.set(0);
        post(http, endpoint + "/request");
        awaitCommitted(observer, key.storageId(), "B", 1, 2, phases);
        assertEquals("B", get(http, endpoint + "/view"));
        assertTrue(observation.sql.stream().anyMatch(s -> s.toLowerCase(Locale.ROOT).contains("consumer_item")
            && s.toLowerCase(Locale.ROOT).matches("(?s).*for update(?: of [\\w., ]+)? nowait\\s*")),
            "Consumer mapping generated no actual NOWAIT point SELECT");
        receipt.put("changed_phase_sql", List.copyOf(observation.sql));
        assertEquals(Set.of("ORACLE_NOWAIT"), observation.lookupPolicies);
        assertEquals(0, observation.dropped.get());
        owner.value.set("C"); assertEquals("ENQUEUED", post(http, endpoint + "/event"));
        awaitCommitted(observer, key.storageId(), "C", 2, 3, phases);
        assertEquals("C", get(http, endpoint + "/view"));
        assertEquals(3, owner.calls.get()); assertEquals(3, observation.ownerCalls.get());
        assertTrue(observation.ownerThreads.stream().allMatch(t -> t.startsWith("model-sync-owner")));
        assertTrue(observation.persistenceThreads.stream().noneMatch(t -> t.startsWith("reactor-http")));
        if ("webflux".equals(mode)) {
          assertTrue(observation.requestThreads.stream().allMatch(t -> t.startsWith("reactor-http")));
          assertTrue(observation.persistenceThreads.stream().allMatch(t -> t.startsWith("model-sync-jpa")));
        } else assertTrue(observation.requestThreads.stream().allMatch(t -> t.startsWith("http-nio")));
        receipt.put("owner_calls", owner.calls.get()); receipt.put("committed_reconciliations", phases.size());
        receipt.put("persistence_threads", observation.persistenceThreads); receipt.put("owner_threads", observation.ownerThreads);
        receipt.put("request_threads", observation.requestThreads); receipt.put("effective_lookup_policies", observation.lookupPolicies);
      } finally {
        if (observation != null) {
          receipt.put("bounded_sql_at_exit", List.copyOf(observation.sql));
          receipt.put("sql_dropped", observation.dropped.get());
          receipt.put("owner_calls_at_exit", observation.ownerCalls.get());
          receipt.put("persistence_threads_at_exit", observation.persistenceThreads);
          receipt.put("owner_threads_at_exit", observation.ownerThreads);
        }
        if (host != null) host.close();
        if (worker != null) {
          var stop = worker.shutdown(); receipt.put("shutdown", Map.of("status", stop.status().name(),
              "elapsed_ms", stop.elapsedMillis(), "physical", stop.physicalOperations(), "owner", stop.ownerOperations()));
          assertEquals(WorkerShutdownStatus.DRAINED, stop.status()); assertEquals(0, worker.physicalOperations());
        }
        if (pool != null) { receipt.put("pool_closed", pool.isClosed()); assertTrue(pool.isClosed()); }
        if (observation != null) { assertEquals(0, observation.ownerActive.get()); receipt.put("owner_active_after_close", 0); }
      }
      HikariDataSource disabledPool;
      try (var disabled = start(mode, false, owner.url())) {
        disabledPool = disabled.getBean(HikariDataSource.class);
        for (var type : List.of(SyncGateway.class, WorkStore.class, QuotaGate.class, ReactorSyncWorker.class))
          assertEquals(0, disabled.getBeansOfType(type).size(), "Disabled Model Sync still created " + type);
        receipt.put("disabled_mode", "PASS");
      }
      assertTrue(disabledPool.isClosed()); receipt.put("disabled_pool_closed", true);
      long drain = System.nanoTime() + Duration.ofSeconds(3).toNanos();
      while (ownedThreads() != 0 && System.nanoTime() < drain) Thread.sleep(20);
      receipt.put("owned_scheduler_threads_after_close", ownedThreads()); assertEquals(0, ownedThreads());
      receipt.put("status", "PASS");
    } catch (Exception | AssertionError failure) {
      receipt.put("status", "FAILED"); receipt.put("failure_type", failure.getClass().getName()); throw failure;
    } finally {
      try {
      observer.update("delete from MS_STATE where work_id=?", key.storageId());
      observer.update("delete from MS_WORK where work_id=?", key.storageId());
      observer.update("delete from MS_COOLDOWN where quota_group='consumer-owner'");
      observer.update("delete from MS_QUOTA where quota_group='consumer-owner'");
      observer.execute("drop table CONSUMER_ITEM purge");
      receipt.put("fixture_rows_remaining", observer.queryForObject("select count(*) from MS_WORK where binding_id='item'", Integer.class));
      assertEquals(0, receipt.get("fixture_rows_remaining"));
      } catch (Exception | AssertionError cleanupFailure) {
        receipt.put("status", "FAILED"); receipt.put("cleanup_failure_type", cleanupFailure.getClass().getName());
        throw cleanupFailure;
      } finally {
      Files.createDirectories(Path.of("target/consumer-evidence"));
      Files.writeString(Path.of("target/consumer-evidence/receipt.json"),
          new tools.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(receipt));
      }
    }
  }

  private static long ownedThreads() {
    return Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive)
        .filter(t -> t.getName().startsWith("model-sync-jpa") || t.getName().startsWith("model-sync-owner")).count();
  }

  private static ConfigurableApplicationContext start(String mode, boolean enabled, String owner) {
    return new SpringApplicationBuilder(ConsumerApplication.class)
        .web("mvc".equals(mode) ? WebApplicationType.SERVLET : WebApplicationType.REACTIVE)
        .run("--server.address=127.0.0.1", "--server.port=0", "--spring.main.banner-mode=off", "--logging.level.root=WARN",
            "--spring.datasource.url=" + System.getenv("MODELSYNC_ORACLE_URL"),
            "--spring.datasource.username=" + System.getenv("MODELSYNC_ORACLE_USER"),
            "--spring.datasource.password=" + System.getenv("MODELSYNC_ORACLE_PASSWORD"),
            "--spring.datasource.hikari.maximum-pool-size=4", "--spring.datasource.hikari.minimum-idle=0",
            "--spring.datasource.hikari.connection-timeout=1000", "--spring.datasource.hikari.validation-timeout=1000",
            "--spring.datasource.hikari.data-source-properties.oracle.jdbc.ReadTimeout=10000",
            "--spring.datasource.hikari.data-source-properties.oracle.net.CONNECT_TIMEOUT=1500",
            "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.open-in-view=false",
            "--model-sync.enabled=" + enabled, "--model-sync.worker-enabled=true", "--model-sync.aop.enabled=false",
            "--model-sync.triggers.events-enabled=true", "--model-sync.quotas.consumer-owner=5ms",
            "--model-sync.concurrency=2", "--model-sync.persistence-threads=2", "--model-sync.owner-threads=2",
            "--model-sync.queued-tasks-per-thread=16", "--model-sync.poll-interval=100ms",
            "--model-sync.productive-interval=10ms", "--model-sync.max-idle-interval=250ms",
            "--model-sync.jdbc.point-lock-policy=ORACLE_NOWAIT", "--model-sync.jdbc.quota-lock-policy=ORACLE_NOWAIT",
            "--model-sync.jdbc.insert-policy=ORACLE_26_NOWAIT", "--model-sync.jdbc.cooldown-policy=DURABLE",
            "--model-sync.jpa.lock-policy=ORACLE_NOWAIT", "--consumer.owner-url=" + owner);
  }
  private static String post(HttpClient http, String url) throws Exception {
    var result = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, result.statusCode()); return result.body(); // Acceptance is checked against committed rows separately.
  }
  private static String get(HttpClient http, String url) throws Exception {
    var result = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, result.statusCode()); return result.body();
  }
  private static void awaitCommitted(JdbcTemplate observer, String id, String value, long revision, long generation,
      List<Map<String, Object>> phases) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos(); Map<String, Object> row = Map.of();
    while (System.nanoTime() < deadline) {
      var rows = observer.queryForList("select p.item_value,p.local_version,s.owner_validator,s.outcome,"
          + "w.generation,w.work_status from CONSUMER_ITEM p join MS_STATE s on s.work_id=p.storage_key "
          + "join MS_WORK w on w.work_id=s.work_id where w.work_id=?", id);
      if (!rows.isEmpty()) row = rows.getFirst();
      if (value.equals(row.get("ITEM_VALUE")) && "IDLE".equals(row.get("WORK_STATUS"))) break;
      Thread.sleep(25);
    }
    phases.add(new LinkedHashMap<>(row)); assertEquals(value, row.get("ITEM_VALUE"));
    assertEquals(value, row.get("OWNER_VALIDATOR")); assertEquals("APPLIED", row.get("OUTCOME"));
    assertEquals("IDLE", row.get("WORK_STATUS"));
    assertEquals(revision, ((Number)row.get("LOCAL_VERSION")).longValue());
    assertEquals(generation, ((Number)row.get("GENERATION")).longValue());
  }
  private static void inspect(ConfigurableApplicationContext host, Map<String, Object> receipt) throws Exception {
    var ds = host.getBean(DataSource.class);
    var manager = assertInstanceOf(JpaTransactionManager.class, host.getBean(PlatformTransactionManager.class));
    assertSame(ds, manager.getDataSource()); receipt.put("datasource_enlisted", true);
    Object work = assertInstanceOf(JdbcWorkStore.class, host.getBean(WorkStore.class));
    Object quota = assertInstanceOf(JdbcQuotaGate.class, host.getBean(QuotaGate.class));
    Object projection = host.getBean(JpaProjectionStore.class);
    assertEquals(JdbcPointLockPolicy.ORACLE_NOWAIT, ReflectionTestUtils.getField(work, "pointLockPolicy"));
    assertEquals(JdbcQuotaLockPolicy.ORACLE_NOWAIT, ReflectionTestUtils.getField(quota, "lockPolicy"));
    assertEquals(JdbcInsertPolicy.ORACLE_26_NOWAIT, ReflectionTestUtils.getField(work, "insertPolicy"));
    assertEquals(JdbcInsertPolicy.ORACLE_26_NOWAIT, ReflectionTestUtils.getField(quota, "insertPolicy"));
    assertEquals(JdbcCooldownPolicy.DURABLE, ((JdbcQuotaGate)quota).cooldownPolicy());
    assertEquals(JpaLockPolicy.ORACLE_NOWAIT, ReflectionTestUtils.getField(projection, "lockPolicy"));
    assertInstanceOf(ItemMapping.class, ReflectionTestUtils.getField(projection, "mapping"));
    assertEquals("consumerClock", host.getBeanFactory().getBeanDefinition("modelSyncClock").getFactoryMethodName());
    assertInstanceOf(DatabaseClock.class, host.getBean("modelSyncClock"));
    receipt.put("clock_replacement", "consumerClock/DatabaseClock");
    var report = ConditionEvaluationReport.get(host.getBeanFactory());
    var matches = report.getConditionAndOutcomesBySource().entrySet().stream()
        .filter(e -> e.getKey().startsWith("io.modelsync.autoconfigure.") && !e.getKey().contains("#") && e.getValue().isFullMatch())
        .map(Map.Entry::getKey).sorted().toList();
    for (String name : List.of("ModelSyncAutoConfiguration", "ModelSyncJdbcAutoConfiguration", "ModelSyncJpaAutoConfiguration",
        "ModelSyncReactorAutoConfiguration", "ModelSyncTriggerAutoConfiguration"))
      assertTrue(matches.contains("io.modelsync.autoconfigure." + name), "Missing discovered configuration: " + name);
    receipt.put("discovered_auto_configurations", matches);
    Map<String, String> locations = new TreeMap<>();
    for (Class<?> type : List.of(SyncGateway.class, JdbcWorkStore.class, JpaProjectionStore.class, ReactorSyncWorker.class, ModelSyncAutoConfiguration.class)) {
      String location = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
      assertTrue(Path.of(location).startsWith(Path.of(System.getenv("CONSUMER_REPOSITORY"))));
      assertTrue(location.endsWith(".jar")); locations.put(type.getName(), location);
    }
    receipt.put("artifact_locations", locations);
    try (var connection = ds.getConnection()) {
      var meta = connection.getMetaData(); assertEquals("Oracle", meta.getDatabaseProductName());
      assertEquals(23, meta.getDatabaseMajorVersion()); assertEquals(26, meta.getDatabaseMinorVersion());
      assertEquals("23.26.3.0.0", meta.getDriverVersion());
      receipt.put("oracle", meta.getDatabaseProductVersion()); receipt.put("jdbc", meta.getDriverVersion());
    }
    assertEquals("7.4.5.Final", org.hibernate.Version.getVersionString());
    receipt.put("hibernate", org.hibernate.Version.getVersionString());
    assertEquals("1", new JdbcTemplate(ds).queryForObject("select meta_value from MS_META where meta_name='cooldown_schema_version'", String.class));
    receipt.put("target", "oracle26-nowait-ledger-first-v1");
    receipt.put("effective_policies", Map.of("point", "ORACLE_NOWAIT", "quota", "ORACLE_NOWAIT",
        "insert", "ORACLE_26_NOWAIT", "jpa", "ORACLE_NOWAIT", "cooldown", "DURABLE", "extension", 1));
  }
}
