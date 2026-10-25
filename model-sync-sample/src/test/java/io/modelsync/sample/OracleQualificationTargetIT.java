package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.Priority;
import io.modelsync.core.application.SyncCoordinator;
import io.modelsync.core.application.SyncGateway;
import io.modelsync.core.domain.SubjectKey;
import io.modelsync.core.domain.WorkKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** All policies consumed together by the real starter, qualified mapping and local JPA manager. */
class OracleQualificationTargetIT {
  @Test void namedTargetConsumesEveryPolicyAndQualifiedMapping() throws Exception {
    assertEquals(QualificationTargetAssertions.TARGET, System.getProperty("modelsync.qualification.target"));
    try (var observer = RecoveryConfiguration.source(System.getenv("MODELSYNC_ORACLE_URL"), 1, 10000)) {
      var jdbc = new JdbcTemplate(observer);
      assertEquals(0, jdbc.queryForObject("select count(*) from user_tables where table_name='SAMPLE_CUSTOMER'", Integer.class));
      new ResourceDatabasePopulator(new ClassPathResource("sample-oracle.sql")).execute(observer);
      var subject = new SubjectKey("synthetic-target", UUID.randomUUID().toString());
      String id = new WorkKey("customer", subject).storageId();
      try (var host = new SpringApplicationBuilder(LockPolicyOracleConfiguration.class).web(WebApplicationType.NONE)
          .profiles("lock-policy-oracle").run("--spring.jpa.hibernate.ddl-auto=validate", "--spring.main.banner-mode=off",
              "--logging.level.root=WARN", "--model-sync.enabled=true", "--model-sync.worker-enabled=false",
              "--model-sync.aop.enabled=false", "--model-sync.quotas.policy-owner=1ms",
              "--model-sync.jdbc.point-lock-policy=ORACLE_NOWAIT", "--model-sync.jdbc.quota-lock-policy=ORACLE_NOWAIT",
              "--model-sync.jdbc.cooldown-policy=DURABLE", "--model-sync.jdbc.insert-policy=ORACLE_26_NOWAIT", "--model-sync.jpa.lock-policy=ORACLE_NOWAIT")) {
        QualificationTargetAssertions.host(host, "starter-target");
        var gateway = host.getBean(SyncGateway.class); var coordinator = host.getBean(SyncCoordinator.class);
        gateway.request("customer", subject.tenant(), subject.id(), Priority.NORMAL, true);
        assertTrue(coordinator.runOne());
        gateway.request("customer", subject.tenant(), subject.id(), Priority.NORMAL, true);
        assertTrue(coordinator.runOne());
        assertEquals("IDLE", jdbc.queryForObject("select work_status from MS_WORK where work_id=?", String.class, id));
        assertEquals(1, jdbc.queryForObject("select count(*) from SAMPLE_CUSTOMER p join MS_STATE s on s.work_id=p.storage_key where s.work_id=?", Integer.class, id));
        assertTrue(host.getBean(LockPolicyObservation.class).sql.stream().anyMatch(sql ->
            sql.toLowerCase(java.util.Locale.ROOT).contains("for update nowait")), "Actual Hibernate SQL, not only a JPA hint");
      } finally {
        jdbc.update("delete from MS_STATE where work_id=?", id); jdbc.update("delete from MS_WORK where work_id=?", id);
        jdbc.execute("drop table SAMPLE_CUSTOMER purge"); jdbc.update("delete from MS_QUOTA where quota_group='policy-owner'");
      }
    }
  }
}
