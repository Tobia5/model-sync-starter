package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.Priority;
import io.modelsync.core.application.SyncCoordinator;
import io.modelsync.core.application.SyncGateway;
import io.modelsync.core.domain.SubjectKey;
import io.modelsync.core.domain.WorkKey;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import io.modelsync.core.FetchResult;
import io.modelsync.core.domain.Digests;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** All policies consumed together by the real starter, qualified mapping and local JPA manager. */
class OracleQualificationTargetIT {
  @Test void namedTargetConsumesEveryPolicyAndQualifiedMapping() throws Exception {
    var evidence = FaultEvidence.report("named-target-changed-existing-projection");
    List<Map<String, Object>> phases = new ArrayList<>(); evidence.put("phases", phases);
    evidence.put("sql_phase", "NOT_RUN");
    try {
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
        var observation = host.getBean(LockPolicyObservation.class);
        try {
          observation.ownerResult.set(FetchResult.found(new Customer("A", "ACTIVE"), "A"));
          gateway.request("customer", subject.tenant(), subject.id(), Priority.NORMAL, true);
          assertTrue(coordinator.runOne());
          var a = committed(jdbc, id); phases.add(Map.of("phase", "created-A", "committed", a));
          assertCommitted(a, "A", "A", 1);
          long revisionA = ((Number) a.get("LOCAL_VERSION")).longValue();
          observation.ownerResult.set(FetchResult.found(new Customer("changed-B", "ACTIVE"), "B"));
          gateway.request("customer", subject.tenant(), subject.id(), Priority.NORMAL, true);
          observation.sql.clear(); observation.sqlDropped.set(0);
          try {
            assertTrue(coordinator.runOne());
            var b = committed(jdbc, id); phases.add(Map.of("phase", "changed-existing-B", "committed", b));
            assertCommitted(b, "changed-B", "B", 2);
            assertEquals(revisionA + 1, ((Number) b.get("LOCAL_VERSION")).longValue());
            assertEquals("A", observation.validatorSeen.get());
            assertEquals(2, observation.ownerCalls.get());
            assertEquals(0, observation.sqlDropped.get(), "Bounded SQL evidence overflow");
            boolean matched = observation.sql.stream().anyMatch(sql -> OracleProjectionLockSql.isNowaitSelect(sql, "sample_customer"));
            evidence.put("matched_nowait_select", matched);
            assertTrue(matched,
                "Changed existing projection SELECT must use FOR UPDATE [OF columns] NOWAIT");
            evidence.put("status", "PASS");
          } finally {
            evidence.put("sql_phase", "changed-existing-B");
            evidence.put("sql", List.copyOf(observation.sql));
            evidence.put("sql_dropped", observation.sqlDropped.get());
          }
        } finally { evidence.put("owner_calls", observation.ownerCalls.get()); }
      } finally {
        jdbc.update("delete from MS_STATE where work_id=?", id); jdbc.update("delete from MS_WORK where work_id=?", id);
        jdbc.execute("drop table SAMPLE_CUSTOMER purge"); jdbc.update("delete from MS_QUOTA where quota_group='policy-owner'");
      }
    }
    } catch (Exception | AssertionError failure) {
      evidence.put("status", "FAILED"); evidence.put("failure_type", failure.getClass().getName()); throw failure;
    } finally { FaultEvidence.write("oracle-target-projection.json", evidence); }
  }

  private static Map<String, Object> committed(JdbcTemplate jdbc, String id) {
    // The observer pool is independent of the closed JpaTransactionManager attempt.
    return jdbc.queryForMap("select p.display_name,p.local_version,s.owner_validator,s.matched_digest,s.outcome,"
        + "w.generation,w.work_status from SAMPLE_CUSTOMER p join MS_STATE s on s.work_id=p.storage_key "
        + "join MS_WORK w on w.work_id=s.work_id where s.work_id=?", id);
  }
  private static void assertCommitted(Map<String, Object> row, String value, String validator, long generation) {
    assertEquals(value, row.get("DISPLAY_NAME")); assertEquals(validator, row.get("OWNER_VALIDATOR"));
    assertEquals(Digests.sha256Fields(value, "ACTIVE"), row.get("MATCHED_DIGEST"));
    assertEquals("APPLIED", row.get("OUTCOME")); assertEquals("IDLE", row.get("WORK_STATUS"));
    assertEquals(generation, ((Number) row.get("GENERATION")).longValue());
  }
}
