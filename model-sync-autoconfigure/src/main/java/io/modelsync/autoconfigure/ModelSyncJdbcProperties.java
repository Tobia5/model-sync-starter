package io.modelsync.autoconfigure;

import io.modelsync.jdbc.JdbcPointLockPolicy;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Adapter-only configuration, loaded only when the JDBC adapter is present. */
@ConfigurationProperties("model-sync.jdbc")
public final class ModelSyncJdbcProperties {
  private JdbcPointLockPolicy pointLockPolicy = JdbcPointLockPolicy.DEFAULT;
  public JdbcPointLockPolicy getPointLockPolicy() { return pointLockPolicy; }
  public void setPointLockPolicy(JdbcPointLockPolicy value) { pointLockPolicy = Objects.requireNonNull(value); }
}
