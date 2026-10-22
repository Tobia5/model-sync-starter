package io.modelsync.sample;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.modelsync.core.FetchResult;
import io.modelsync.core.RefreshMode;
import io.modelsync.core.application.ModelBinding;
import io.modelsync.core.domain.Digests;
import io.modelsync.core.domain.FreshnessPolicy;
import io.modelsync.jpa.JpaProjectionStore;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Test-only actual starter host. Timeouts affect only this fixture-owned datasource. */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackageClasses = CustomerEntity.class)
@Profile("recovery-oracle")
class RecoveryConfiguration {
  static HikariDataSource source(String url, int size) {
    return source(url, size, 2000);
  }
  static HikariDataSource source(String url, int size, int readTimeout) {
    String user = System.getenv("MODELSYNC_ORACLE_USER");
    if (!"DEDICATED_TEST_SCHEMA".equals(System.getenv("MODELSYNC_ORACLE_TEST_SCHEMA_ACK"))
        || user == null || !user.matches("MS_TEST_[A-Za-z0-9_]{1,20}"))
      throw new IllegalStateException("Dedicated disposable Oracle runtime schema required");
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(url); config.setUsername(user); config.setPassword(System.getenv("MODELSYNC_ORACLE_PASSWORD"));
    config.setMaximumPoolSize(size); config.setMinimumIdle(0); config.setConnectionTimeout(1000);
    config.setValidationTimeout(500); config.setInitializationFailTimeout(-1); config.setMaxLifetime(0);
    config.addDataSourceProperty("oracle.net.CONNECT_TIMEOUT", "1500");
    config.addDataSourceProperty("oracle.jdbc.ReadTimeout", Integer.toString(readTimeout));
    // The owned TCP relay and Docker forwarding do not relay urgent TCP data.
    config.addDataSourceProperty("oracle.net.disableOob", "true");
    config.setPoolName("modelsync-fault-owned-" + java.util.UUID.randomUUID());
    return new HikariDataSource(config);
  }
  @Bean(destroyMethod = "close") HikariDataSource recoveryDataSource(Environment env) {
    if (!"validate".equals(env.getProperty("spring.jpa.hibernate.ddl-auto")))
      throw new IllegalStateException("Fault host refuses automatic schema creation/deletion");
    return source(env.getProperty("modelsync.test.jdbc-url", System.getenv("MODELSYNC_ORACLE_URL")), 4);
  }
  @Bean RecoveryControl recoveryControl(Environment env) { return new RecoveryControl(env); }
  @Bean ModelBinding<Customer> recoveryBinding(EntityManagerFactory emf, RecoveryControl control) {
    var projection = new JpaProjectionStore<>(SharedEntityManagerCreator.createSharedEntityManager(emf), new RecoveryJpaMapping());
    return ModelBinding.builder("customer", Customer.class).boundary("synthetic-recovery")
        .owner("synthetic-recovery-owner", request -> {
          if (TransactionSynchronizationManager.isActualTransactionActive()) throw new AssertionError("Owner inside transaction");
          if (control.ownerOverride != null) return control.ownerOverride.fetch(request);
          control.ownerRead();
          return FetchResult.found(new Customer(control.value, "ACTIVE"), control.value);
        }).replica(new RecoveryProjection(projection, control)).repairFromOwner()
        .fingerprint(c -> Digests.sha256Fields(c.displayName(), c.status())).quota("recovery-owner")
        .refreshMode(RefreshMode.ON_DEMAND)
        .freshness(new FreshnessPolicy(Duration.ofHours(1), Duration.ofDays(1), Duration.ofDays(2))).build();
  }
}
