package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.application.ModelBinding;
import io.modelsync.core.port.QuotaGate;
import io.modelsync.core.port.WorkStore;
import io.modelsync.jdbc.*;
import io.modelsync.jpa.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/** Test-only inspection of actual adapter settings, never entity copying or production reflection. */
final class QualificationTargetAssertions {
  static final String TARGET = "oracle26-nowait-ledger-first-v1";
  private QualificationTargetAssertions() {}

  static boolean named() {
    return TARGET.equals(System.getProperty("modelsync.qualification.target"))
        || TARGET.equals(System.getenv("MODELSYNC_QUALIFICATION_TARGET"));
  }

  static void policies(Object work, Object quota, Object projection) {
    assertInstanceOf(JdbcWorkStore.class, work);
    if (quota instanceof WorkerQuota) quota = ReflectionTestUtils.getField(quota, "delegate");
    if (projection instanceof RecoveryProjection || projection instanceof AtomicityProjection)
      projection = ReflectionTestUtils.getField(projection, "delegate");
    assertInstanceOf(JdbcQuotaGate.class, quota);
    assertInstanceOf(JpaProjectionStore.class, projection);
    assertEquals(JdbcPointLockPolicy.ORACLE_NOWAIT, ReflectionTestUtils.getField(work, "pointLockPolicy"));
    assertEquals(JdbcInsertPolicy.ORACLE_26_NOWAIT, ReflectionTestUtils.getField(work, "insertPolicy"));
    assertEquals(JdbcQuotaLockPolicy.ORACLE_NOWAIT, ReflectionTestUtils.getField(quota, "lockPolicy"));
    assertEquals(JdbcInsertPolicy.ORACLE_26_NOWAIT, ReflectionTestUtils.getField(quota, "insertPolicy"));
    assertEquals(JpaLockPolicy.ORACLE_NOWAIT, ReflectionTestUtils.getField(projection, "lockPolicy"));
    Object mapping = ReflectionTestUtils.getField(projection, "mapping");
    assertTrue(mapping instanceof CustomerJpaMapping || mapping instanceof RecoveryJpaMapping,
        "Named target accepts only the actually qualified mappings, not arbitrary replacements");
  }

  static void host(ConfigurableApplicationContext context, String role) {
    try {
      var source = context.getBean(DataSource.class);
      var manager = assertInstanceOf(JpaTransactionManager.class, context.getBean(PlatformTransactionManager.class));
      assertSame(source, manager.getDataSource());
      var work = context.getBean(WorkStore.class);
      var quota = context.getBean(QuotaGate.class);
      for (ModelBinding<?> binding : context.getBeansOfType(ModelBinding.class).values())
        policies(work, quota, binding.projection());
      Map<String, Object> receipt = new LinkedHashMap<>(FaultEvidence.report("named-qualification-host"));
      receipt.put("qualification_target", TARGET); receipt.put("claim_scope", "named-configuration-only");
      receipt.put("role", role); receipt.put("pid", ProcessHandle.current().pid());
      receipt.put("point_lock_policy", "ORACLE_NOWAIT"); receipt.put("quota_lock_policy", "ORACLE_NOWAIT");
      receipt.put("insert_policy", "ORACLE_26_NOWAIT"); receipt.put("jpa_lock_policy", "ORACLE_NOWAIT");
      receipt.put("actual_adapter_settings_verified", true); receipt.put("qualified_mapping_consumed", true);
      receipt.put("datasource_enlisted", true); receipt.put("writer_protocol", "ledger-first");
      try (var connection = source.getConnection()) {
        var metadata = connection.getMetaData();
        assertEquals("Oracle", metadata.getDatabaseProductName());
        assertEquals(23, metadata.getDatabaseMajorVersion()); assertEquals(26, metadata.getDatabaseMinorVersion());
        assertTrue(metadata.getDatabaseProductVersion().contains("26ai"));
        assertTrue(metadata.getDatabaseProductVersion().contains("23.26.3.0.0"));
        assertEquals("23.26.3.0.0", metadata.getDriverVersion());
        receipt.put("oracle", metadata.getDatabaseProductVersion()); receipt.put("oracle_major", 23);
        receipt.put("oracle_minor", 26); receipt.put("driver", metadata.getDriverVersion());
      }
      receipt.put("hibernate", org.hibernate.Version.getVersionString()); receipt.put("status", "PASS");
      FaultEvidence.write("qualification-host-" + UUID.randomUUID() + ".json", receipt);
    } catch (Exception error) { throw new IllegalStateException("Named qualification host verification failed", error); }
  }
}
