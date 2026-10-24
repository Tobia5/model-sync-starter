package io.modelsync.sample;

import io.modelsync.core.application.ModelBinding;
import io.modelsync.core.FetchResult;
import io.modelsync.core.domain.FreshnessPolicy;
import io.modelsync.core.domain.Digests;
import io.modelsync.jpa.JpaProjectionStore;
import io.modelsync.jpa.JpaLockPolicy;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@TestConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackageClasses = CustomerEntity.class)
class AtomicityConfiguration {
  @Bean AtomicityFaults atomicityFaults() { return new AtomicityFaults(); }

  @Bean ModelBinding<Customer> atomicityBinding(
      EntityManagerFactory emf, DataSource source, AtomicityFaults faults, JpaLockPolicy lockPolicy) {
    var projection = new JpaProjectionStore<>(
        SharedEntityManagerCreator.createSharedEntityManager(emf), new CustomerJpaMapping(), lockPolicy);
    return ModelBinding.builder("customer", Customer.class)
        .boundary("synthetic-atomicity")
        .owner("synthetic-owner", request -> {
          if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new AssertionError("Owner called under a database transaction");
          return FetchResult.found(new Customer("before", "ACTIVE"), "before");
        })
        .replica(new AtomicityProjection(projection, faults, new JdbcTemplate(source)))
        .repairFromOwner().fingerprint(c -> Digests.sha256Fields(c.displayName(), c.status()))
        .quota("atomicity-owner")
        .freshness(new FreshnessPolicy(Duration.ofHours(1), Duration.ofDays(1), Duration.ofDays(2)))
        .build();
  }
}
