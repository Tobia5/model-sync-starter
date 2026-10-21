package io.modelsync.sample;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.modelsync.autoconfigure.ModelSyncProperties;
import io.modelsync.core.RefreshMode;
import io.modelsync.core.application.ModelBinding;
import io.modelsync.core.domain.Digests;
import io.modelsync.core.domain.FreshnessPolicy;
import io.modelsync.jdbc.JdbcQuotaGate;
import io.modelsync.jdbc.JdbcWorkStore;
import io.modelsync.jpa.JpaProjectionStore;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;

/** Test-only host: actual starter coordinator/worker, JPA projection and instrumented JDBC ports. */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackageClasses = CustomerEntity.class)
@Profile("worker-oracle")
class WorkerRuntimeConfiguration {
  @Bean(destroyMethod = "close") HikariDataSource workerDataSource() throws Exception {
    String user = System.getenv("MODELSYNC_ORACLE_USER");
    if (!"DEDICATED_TEST_SCHEMA".equals(System.getenv("MODELSYNC_ORACLE_TEST_SCHEMA_ACK"))
        || user == null || !user.matches("MS_TEST_[A-Za-z0-9_]{1,20}"))
      throw new IllegalStateException("Dedicated disposable Oracle runtime schema required");
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(System.getenv("MODELSYNC_ORACLE_URL"));
    config.setUsername(user); config.setPassword(System.getenv("MODELSYNC_ORACLE_PASSWORD"));
    config.setMaximumPoolSize(8); config.setMinimumIdle(8); config.setMaxLifetime(0);
    config.setConnectionInitSql("alter session set statistics_level=all");
    config.setPoolName("modelsync-actual-worker");
    var source = new HikariDataSource(config);
    var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
    if (jdbc.queryForObject("select count(*) from user_tables where table_name='SAMPLE_CUSTOMER'", Integer.class) != 0) {
      source.close();
      throw new IllegalStateException("Worker fixture refuses an existing projection table");
    }
    new ResourceDatabasePopulator(new ClassPathResource("sample-oracle.sql")).execute(source);
    // Establish all eight physical sessions before observing any session-counter intervals.
    var connections = new java.util.ArrayList<java.sql.Connection>();
    try { for (int i = 0; i < 8; i++) connections.add(source.getConnection()); }
    finally { for (var connection : connections) connection.close(); }
    return source;
  }

  @Bean WorkerJdbcTemplate workerJdbc(HikariDataSource source) { return new WorkerJdbcTemplate(source); }
  @Bean JdbcWorkStore workerStore(WorkerJdbcTemplate jdbc) {
    var store = new JdbcWorkStore(jdbc); store.verifySchema(); return store;
  }
  @Bean WorkerQuota workerQuota(WorkerJdbcTemplate jdbc, PlatformTransactionManager manager,
      @Qualifier("modelSyncClock") Clock clock, ModelSyncProperties properties) {
    var gate = new JdbcQuotaGate(jdbc, manager, clock);
    properties.getQuotas().forEach(gate::register);
    return new WorkerQuota(gate);
  }
  @Bean WorkerOwner workerOwner() { return new WorkerOwner(); }
  @Bean ModelBinding<Customer> workerBinding(EntityManagerFactory emf, WorkerOwner owner) {
    return binding("worker-main", "worker-main", emf, owner);
  }
  @Bean ModelBinding<Customer> independentWorkerBinding(EntityManagerFactory emf, WorkerOwner owner) {
    return binding("worker-other", "worker-other", emf, owner);
  }
  private ModelBinding<Customer> binding(String id, String quota, EntityManagerFactory emf, WorkerOwner owner) {
    return ModelBinding.builder(id, Customer.class).boundary("synthetic-worker")
        .owner("synthetic-" + id, owner)
        .replica(new JpaProjectionStore<>(SharedEntityManagerCreator.createSharedEntityManager(emf), new CustomerJpaMapping()))
        .repairFromOwner().fingerprint(c -> Digests.sha256Fields(c.displayName(), c.status()))
        .quota(quota).refreshMode(RefreshMode.ON_DEMAND)
        .freshness(new FreshnessPolicy(Duration.ofHours(1), Duration.ofDays(1), Duration.ofDays(2))).build();
  }
}
