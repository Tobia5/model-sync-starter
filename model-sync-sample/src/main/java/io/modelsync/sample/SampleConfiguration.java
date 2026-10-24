package io.modelsync.sample;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import io.modelsync.jpa.*;
import io.modelsync.spring.*;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

/** Sample beans for demo datasource, owner adapter, projection store, and customer binding. */
@Configuration(proxyBeanMethods = false)
public class SampleConfiguration {
  @Bean
  @Profile("demo")
  DataSource demoDataSource() {
    var source = new DriverManagerDataSource("jdbc:h2:mem:model_sync;DB_CLOSE_DELAY=-1", "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource("model-sync/schema/h2-v1.sql"))
        .execute(source);
    return source;
  }

  @Bean
  @Profile("demo & !rest-owner")
  @DataOwner("customer")
  BlockingOwner<Customer> demoCustomerOwner() {
    return request -> FetchResult.found(new Customer("Example Customer", "ACTIVE"), "demo-v1");
  }

  @Bean
  @DataCache("customer")
  JpaProjectionStore<Customer, CustomerEntity> customerCache(EntityManagerFactory emf, JpaLockPolicy lockPolicy) {
    return new JpaProjectionStore<>(
        SharedEntityManagerCreator.createSharedEntityManager(emf), new CustomerJpaMapping(), lockPolicy);
  }

  @Bean
  ModelBinding<Customer> customerBinding(
      @DataOwner("customer") BlockingOwner<Customer> owner,
      @DataCache("customer") ProjectionStore<Customer> cache) {
    return ModelBinding.builder("customer", Customer.class)
        .boundary("cards")
        .owner("external-customer-system", owner)
        .cache(cache)
        .fingerprint(c -> Digests.sha256Fields("customer-map-v1", c.displayName(), c.status()))
        .quota("customer-owner")
        .freshness(new FreshnessPolicy(Duration.ofHours(1), Duration.ofDays(1), Duration.ofDays(2)))
        .repairFromOwner()
        .build();
  }

  @Bean
  ModelSync<Customer> customerSync(SyncGateway gateway) {
    return gateway.model(Customer.class);
  }
}
