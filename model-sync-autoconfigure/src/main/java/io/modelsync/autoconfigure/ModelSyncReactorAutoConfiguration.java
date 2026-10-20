package io.modelsync.autoconfigure;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import io.modelsync.reactor.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import reactor.core.scheduler.*;

/** Reactor auto-configuration for asynchronous gateway and worker scheduling. */
@AutoConfiguration(after = ModelSyncAutoConfiguration.class)
@ConditionalOnClass(ReactorSyncWorker.class)
@ConditionalOnBean(SyncCoordinator.class)
public class ModelSyncReactorAutoConfiguration {
  @Bean(name = "modelSyncPersistenceScheduler", destroyMethod = "dispose")
  @ConditionalOnMissingBean(name = "modelSyncPersistenceScheduler")
  Scheduler modelSyncPersistenceScheduler(ModelSyncProperties p) {
    p.validateBounds();
    return Schedulers.newBoundedElastic(
        p.getPersistenceThreads(), p.getQueuedTasksPerThread(), "model-sync-jpa");
  }

  @Bean(name = "modelSyncOwnerScheduler", destroyMethod = "dispose")
  @ConditionalOnMissingBean(name = "modelSyncOwnerScheduler")
  Scheduler modelSyncOwnerScheduler(ModelSyncProperties p) {
    p.validateBounds();
    return Schedulers.newBoundedElastic(
        p.getOwnerThreads(), p.getQueuedTasksPerThread(), "model-sync-owner");
  }

  @Bean
  @ConditionalOnMissingBean(ReactiveSyncGateway.class)
  ReactiveSyncGateway reactiveSyncGateway(
      SyncGateway gateway, @Qualifier("modelSyncPersistenceScheduler") Scheduler persistence) {
    return new ReactiveSyncGateway(gateway, persistence);
  }

  @Bean
  @ConditionalOnMissingBean(ReactorSyncWorker.class)
  @ConditionalOnProperty(prefix = "model-sync", name = "worker-enabled", havingValue = "true")
  ReactorSyncWorker modelSyncWorker(
      SyncCoordinator coordinator,
      ModelSyncProperties p,
      @Qualifier("modelSyncPersistenceScheduler") Scheduler persistence,
      @Qualifier("modelSyncOwnerScheduler") Scheduler owner) {
    return new ReactorSyncWorker(
        coordinator, persistence, owner, p.getConcurrency(), p.getPollInterval());
  }
}
