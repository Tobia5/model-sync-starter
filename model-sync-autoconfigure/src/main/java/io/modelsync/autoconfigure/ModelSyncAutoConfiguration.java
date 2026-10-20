package io.modelsync.autoconfigure;

import io.modelsync.core.*;
import io.modelsync.spring.SpringLocalTransactions;
import java.time.Clock;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

/** Core Model Sync auto-configuration for registry, gateway, coordinator, and defaults. */
@AutoConfiguration(after = ModelSyncJdbcAutoConfiguration.class)
@ConditionalOnProperty(prefix = "model-sync", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ModelSyncProperties.class)
public class ModelSyncAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(ModelRegistry.class)
  ModelRegistry modelSyncRegistry(ObjectProvider<ModelBinding<?>> bindings) {
    var definitions = bindings.orderedStream().toList();
    if (definitions.isEmpty())
      throw new IllegalStateException("Model Sync enabled without any ModelBinding beans");
    return new ModelRegistry(definitions);
  }

  @Bean(name = "modelSyncClock")
  @ConditionalOnMissingBean(name = "modelSyncClock")
  Clock modelSyncClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean(LocalTransactions.class)
  SpringLocalTransactions modelSyncTransactions(PlatformTransactionManager manager) {
    return new SpringLocalTransactions(manager);
  }

  @Bean
  @ConditionalOnMissingBean(SyncOptions.class)
  SyncOptions modelSyncOptions(ModelSyncProperties p) {
    p.validateBounds();
    return new SyncOptions(
        p.getLease(), p.getFetchTimeout(), p.getRetryBase(), p.getRetryMax(), p.getMaxAttempts());
  }

  @Bean
  @ConditionalOnMissingBean(WeightedPriorities.class)
  WeightedPriorities modelSyncPriorities() {
    return WeightedPriorities.defaults();
  }

  @Bean
  @ConditionalOnMissingBean(SyncGateway.class)
  SyncGateway modelSyncGateway(
      ModelRegistry registry,
      WorkStore store,
      LocalTransactions tx,
      @Qualifier("modelSyncClock") Clock clock) {
    return new SyncGateway(registry, store, tx, clock);
  }

  @Bean
  @ConditionalOnMissingBean(SyncCoordinator.class)
  SyncCoordinator modelSyncCoordinator(
      ModelRegistry registry,
      WorkStore store,
      LocalTransactions tx,
      QuotaGate gate,
      @Qualifier("modelSyncClock") Clock clock,
      SyncOptions options,
      WeightedPriorities priorities) {
    return new SyncCoordinator(
        registry,
        store,
        tx,
        gate,
        clock,
        options,
        priorities,
        () -> ThreadLocalRandom.current().nextDouble());
  }
}
