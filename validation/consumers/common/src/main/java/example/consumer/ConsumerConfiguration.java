package example.consumer;

import io.modelsync.core.FetchResult;
import io.modelsync.core.FreshnessPolicy;
import io.modelsync.core.RefreshMode;
import io.modelsync.core.application.ModelBinding;
import io.modelsync.core.domain.Digests;
import io.modelsync.jdbc.DatabaseClock;
import io.modelsync.jpa.JpaLockPolicy;
import io.modelsync.jpa.JpaProjectionStore;
import jakarta.persistence.EntityManagerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "model-sync.enabled", havingValue = "true")
public class ConsumerConfiguration {
  @Bean ConsumerObservation observation() { return new ConsumerObservation(); }
  @Bean HibernatePropertiesCustomizer sqlObservation(ConsumerObservation observation) {
    return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
  }
  // Documented named-bean replacement. Retains the database clock for distributed admission.
  @Bean(name = "modelSyncClock") Clock consumerClock(DataSource source) {
    return new DatabaseClock(new JdbcTemplate(source));
  }
  @Bean(destroyMethod = "close") HttpClient syntheticOwnerClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
  }
  @Bean JpaProjectionStore<ItemValue, ItemEntity> itemProjection(EntityManagerFactory factory,
      JpaLockPolicy policy, ConsumerObservation observation) {
    return new JpaProjectionStore<>(SharedEntityManagerCreator.createSharedEntityManager(factory),
        new ItemMapping(observation), policy);
  }
  @Bean ModelBinding<ItemValue> itemBinding(JpaProjectionStore<ItemValue, ItemEntity> projection,
      ConsumerObservation observation, HttpClient syntheticOwnerClient,
      org.springframework.core.env.Environment environment) {
    URI endpoint = URI.create(environment.getRequiredProperty("consumer.owner-url"));
    if (!"127.0.0.1".equals(endpoint.getHost()) || !"http".equals(endpoint.getScheme()))
      throw new IllegalArgumentException("Fixture owner must use loopback HTTP");
    return ModelBinding.builder("item", ItemValue.class).boundary("synthetic-consumer")
        .owner("local-synthetic-owner", request -> {
          if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Owner called under transaction");
          observation.ownerThreads.add(Thread.currentThread().getName());
          observation.ownerActive.incrementAndGet(); observation.ownerCalls.incrementAndGet();
          try {
            var response = syntheticOwnerClient.send(HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 80)
              throw new IllegalStateException("Synthetic response invalid");
            return FetchResult.found(new ItemValue(response.body()), response.headers().firstValue("ETag").orElseThrow());
          } catch (java.io.IOException failure) { throw new IllegalStateException("Synthetic owner transport", failure);
          } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure);
          } finally { observation.ownerActive.decrementAndGet(); }
        }).replica(projection).repairFromOwner().quota("consumer-owner")
        .refreshMode(RefreshMode.ON_DEMAND).fingerprint(value -> Digests.sha256Fields(value.value()))
        .freshness(new FreshnessPolicy(Duration.ofHours(1), Duration.ofDays(1), Duration.ofDays(2))).build();
  }
}
