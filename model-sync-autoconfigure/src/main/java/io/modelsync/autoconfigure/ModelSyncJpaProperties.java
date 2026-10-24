package io.modelsync.autoconfigure;

import io.modelsync.jpa.JpaLockPolicy;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Properties for the explicit JPA policy bean consumed by application-owned projection bindings. */
@ConfigurationProperties("model-sync.jpa")
public final class ModelSyncJpaProperties {
  private JpaLockPolicy lockPolicy = JpaLockPolicy.DEFAULT;
  public JpaLockPolicy getLockPolicy() { return lockPolicy; }
  public void setLockPolicy(JpaLockPolicy value) { lockPolicy = Objects.requireNonNull(value); }
}
