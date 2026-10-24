package io.modelsync.sample;

import com.zaxxer.hikari.HikariDataSource;
import io.modelsync.core.FetchResult;
import io.modelsync.core.RefreshMode;
import io.modelsync.core.application.ModelBinding;
import io.modelsync.core.domain.Digests;
import io.modelsync.core.domain.FreshnessPolicy;
import io.modelsync.jdbc.JdbcWorkStore;
import io.modelsync.jpa.JpaProjectionStore;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackageClasses = CustomerEntity.class)
@Profile("nowait-oracle")
class NowaitOracleConfiguration {
  @Bean(destroyMethod = "close") HikariDataSource nowaitSource() {
    return RecoveryConfiguration.source(System.getenv("MODELSYNC_ORACLE_URL"), 3, 10000);
  }
  @Bean NowaitSqlProbe nowaitSqlProbe() { return new NowaitSqlProbe(); }
  @Bean NowaitPointJdbcTemplate nowaitJdbc(HikariDataSource source, NowaitSqlProbe trace) {
    return new NowaitPointJdbcTemplate(source, trace);
  }
  @Bean JdbcWorkStore nowaitStore(NowaitPointJdbcTemplate jdbc) {
    var store = new JdbcWorkStore(jdbc); store.verifySchema(); return store;
  }
  @Bean HibernatePropertiesCustomizer nowaitSqlCapture(NowaitSqlProbe trace) {
    return properties -> { properties.put("hibernate.session_factory.statement_inspector", trace);
      properties.put("hibernate.jdbc.batch_size", 0); };
  }
  @Bean ModelBinding<Customer> nowaitBinding(EntityManagerFactory emf, NowaitSqlProbe trace) {
    var em = SharedEntityManagerCreator.createSharedEntityManager(emf);
    var projection = new JpaProjectionStore<>(em, new NowaitJpaMapping(em, trace));
    return ModelBinding.builder("customer", Customer.class).boundary("synthetic-nowait")
        .owner("synthetic-owner", request -> {
          if (TransactionSynchronizationManager.isActualTransactionActive()) throw new AssertionError("Owner under transaction");
          return FetchResult.found(new Customer("after", "ACTIVE"), "after");
        }).replica(projection).repairFromOwner().quota("nowait-owner")
        .refreshMode(RefreshMode.ON_DEMAND).fingerprint(c -> Digests.sha256Fields(c.displayName(), c.status()))
        .freshness(new FreshnessPolicy(Duration.ofHours(1), Duration.ofDays(1), Duration.ofDays(2))).build();
  }
}
