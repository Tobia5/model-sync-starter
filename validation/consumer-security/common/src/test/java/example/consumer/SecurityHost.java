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

final class SecurityHost {
  static ConfigurableApplicationContext start(String mode, boolean enabled, String owner) {
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
  static void inspect(ConfigurableApplicationContext host, Map<String, Object> receipt) throws Exception {
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
  static long ownedThreads() {
    return Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive)
        .filter(t -> t.getName().startsWith("model-sync-jpa") || t.getName().startsWith("model-sync-owner")).count();
  }
}
