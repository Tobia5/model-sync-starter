package io.modelsync.sample;

import com.zaxxer.hikari.HikariDataSource;
import io.modelsync.core.*;
import io.modelsync.core.application.ModelBinding;
import io.modelsync.core.domain.*;
import io.modelsync.jpa.*;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Actual starter JDBC/quotas/transaction manager; only owner and observation are test fixtures. */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackageClasses = CustomerEntity.class)
@Profile("lock-policy-oracle")
class LockPolicyOracleConfiguration {
  @Bean(destroyMethod = "close") HikariDataSource policyDataSource() {
    return RecoveryConfiguration.source(System.getenv("MODELSYNC_ORACLE_URL"), 3, 10000);
  }
  @Bean LockPolicyObservation policyObservation(HikariDataSource source) throws Exception {
    return new LockPolicyObservation(source);
  }
  @Bean HibernatePropertiesCustomizer policySqlCapture(LockPolicyObservation observation) {
    return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
  }
  @Bean ModelBinding<Customer> policyBinding(
      EntityManagerFactory emf, JpaLockPolicy policy, LockPolicyObservation observation) {
    var projection = new JpaProjectionStore<>(SharedEntityManagerCreator.createSharedEntityManager(emf),
        new CustomerJpaMapping(), policy);
    return ModelBinding.builder("customer", Customer.class).boundary("synthetic-lock-policy")
        .owner("synthetic-owner", request -> {
          if (TransactionSynchronizationManager.isActualTransactionActive()) throw new AssertionError("Owner under transaction");
          observation.ownerCalls.incrementAndGet(); observation.validatorSeen.set(request.validator());
          return observation.ownerResult.get();
        }).replica(projection).repairFromOwner().quota("policy-owner").refreshMode(RefreshMode.ON_DEMAND)
        .fingerprint(c -> Digests.sha256Fields(c.displayName(), c.status()))
        .freshness(new FreshnessPolicy(Duration.ofHours(1), Duration.ofDays(1), Duration.ofDays(2))).build();
  }
}
