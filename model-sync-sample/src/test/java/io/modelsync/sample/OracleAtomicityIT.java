package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;

/** Real starter auto-configuration, JpaTransactionManager, JPA projection and JDBC ledger. */
@SpringBootTest(classes = OracleAtomicityConfiguration.class, properties = {
    "spring.profiles.active=atomicity-oracle", "spring.jpa.hibernate.ddl-auto=validate",
    "model-sync.enabled=true", "model-sync.worker-enabled=false", "model-sync.aop.enabled=false",
    "model-sync.quotas.atomicity-owner=1ms"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OracleAtomicityIT extends AtomicityChecks {
  @Test void failureInActualOracleCommitRollsBackBoth() throws Exception {
    Exception failure = failedCompletion(AtomicityFault.ORACLE_COMMIT);
    assertTrue(faults.completionReached, "Deferred violation must reach physical COMMIT");
    assertTrue(faults.deferredStatementsAccepted, "Both deferred duplicates must succeed before physical COMMIT");
    StringBuilder causes = new StringBuilder();
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) causes.append(cause.getMessage());
    assertTrue(causes.toString().contains("MS_TEST_ATOMIC_UQ"), "Failure must identify the deferred Oracle constraint");
    assertEquals(0, jdbc.queryForObject("select count(*) from MS_TEST_ATOMIC_COMMIT", Integer.class));
  }

  @AfterAll void removeOwnedTables() {
    jdbc.execute("alter table MS_STATE drop constraint MS_TEST_ATOMIC_STATE_CK");
    jdbc.execute("drop table MS_TEST_ATOMIC_COMMIT purge");
    jdbc.execute("drop table SAMPLE_CUSTOMER purge");
    jdbc.update("delete from MS_QUOTA where quota_group='atomicity-owner'");
  }
}
