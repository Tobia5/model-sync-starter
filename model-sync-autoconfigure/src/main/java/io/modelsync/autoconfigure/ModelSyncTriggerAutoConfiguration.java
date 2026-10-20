package io.modelsync.autoconfigure;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.application.SyncCoordinator;
import io.modelsync.core.application.SyncGateway;
import io.modelsync.core.application.SyncJob;
import io.modelsync.core.application.SyncTriggers;
import io.modelsync.spring.ScheduledSyncJob;
import io.modelsync.spring.SyncEventListener;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = ModelSyncAutoConfiguration.class)
@ConditionalOnBean({SyncGateway.class, SyncCoordinator.class})
@ConditionalOnProperty(prefix = "model-sync", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ModelSyncTriggerProperties.class)
public class ModelSyncTriggerAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(SyncTriggers.class)
  SyncTriggers modelSyncTriggers(SyncGateway gateway) { return new SyncTriggers(gateway); }

  @Bean
  @ConditionalOnMissingBean(SyncJob.class)
  SyncJob modelSyncJob(SyncCoordinator coordinator) { return new SyncJob(coordinator); }

  @Bean
  @ConditionalOnMissingBean(SyncEventListener.class)
  @ConditionalOnProperty(prefix = "model-sync.triggers", name = "events-enabled", havingValue = "true")
  SyncEventListener modelSyncEventListener(SyncTriggers triggers) { return new SyncEventListener(triggers); }

  @Bean
  @ConditionalOnMissingBean(ScheduledSyncJob.class)
  @ConditionalOnProperty(prefix = "model-sync.triggers.job", name = "enabled", havingValue = "true")
  ScheduledSyncJob modelSyncScheduledJob(SyncJob job, ModelSyncTriggerProperties properties, ModelSyncProperties runtime) {
    if (runtime.isWorkerEnabled()) {
      throw new IllegalStateException("Choose either worker-enabled or triggers.job.enabled in one application");
    }
    var p = properties.getJob();
    return new ScheduledSyncJob(job, p.getCron(), ZoneId.of(p.getZone()), p.getMaxItems(), p.getMaxDuration());
  }
}
