package io.modelsync.autoconfigure;

import io.modelsync.jpa.JpaLockPolicy;
import io.modelsync.jpa.JpaProjectionStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Replaceable policy bean; application-owned projection stores explicitly consume it. */
@AutoConfiguration
@ConditionalOnClass(JpaProjectionStore.class)
@ConditionalOnProperty(prefix = "model-sync", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ModelSyncJpaProperties.class)
public class ModelSyncJpaAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(JpaLockPolicy.class)
  JpaLockPolicy modelSyncJpaLockPolicy(ModelSyncJpaProperties properties) {
    return properties.getLockPolicy();
  }
}
