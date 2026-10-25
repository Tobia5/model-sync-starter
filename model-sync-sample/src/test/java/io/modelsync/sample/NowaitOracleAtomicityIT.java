package io.modelsync.sample;

import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;

/** Reuses every retained Oracle atomicity assertion with both production lock policies enabled. */
@SpringBootTest(classes = OracleAtomicityConfiguration.class, properties = {
    "spring.profiles.active=atomicity-oracle", "spring.jpa.hibernate.ddl-auto=validate",
    "model-sync.enabled=true", "model-sync.worker-enabled=false", "model-sync.aop.enabled=false",
    "model-sync.quotas.atomicity-owner=1ms", "model-sync.jdbc.point-lock-policy=ORACLE_NOWAIT",
    "model-sync.jdbc.quota-lock-policy=ORACLE_NOWAIT", "model-sync.jdbc.insert-policy=ORACLE_26_NOWAIT",
    "model-sync.jdbc.cooldown-policy=DURABLE", "model-sync.jpa.lock-policy=ORACLE_NOWAIT"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NowaitOracleAtomicityIT extends OracleAtomicityIT {
  @org.springframework.beans.factory.annotation.Autowired org.springframework.context.ConfigurableApplicationContext context;
  @org.junit.jupiter.api.BeforeAll void verifyNamedAtomicityHost() {
    QualificationTargetAssertions.host(context, "nowait-atomicity");
  }
}
