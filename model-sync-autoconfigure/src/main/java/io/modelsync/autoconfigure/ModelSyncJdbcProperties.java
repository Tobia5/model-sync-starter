package io.modelsync.autoconfigure;

import io.modelsync.jdbc.JdbcPointLockPolicy;
import io.modelsync.jdbc.JdbcQuotaLockPolicy;
import io.modelsync.jdbc.JdbcInsertPolicy;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Adapter-only configuration, loaded only when the JDBC adapter is present. */
@ConfigurationProperties("model-sync.jdbc")
public final class ModelSyncJdbcProperties {
  private JdbcPointLockPolicy pointLockPolicy = JdbcPointLockPolicy.DEFAULT;
  private JdbcQuotaLockPolicy quotaLockPolicy = JdbcQuotaLockPolicy.DEFAULT;
  private JdbcInsertPolicy insertPolicy = JdbcInsertPolicy.DEFAULT;
  public JdbcPointLockPolicy getPointLockPolicy() { return pointLockPolicy; }
  public void setPointLockPolicy(JdbcPointLockPolicy value) { pointLockPolicy = Objects.requireNonNull(value); }
  public JdbcQuotaLockPolicy getQuotaLockPolicy() { return quotaLockPolicy; }
  public void setQuotaLockPolicy(JdbcQuotaLockPolicy value) { quotaLockPolicy = Objects.requireNonNull(value); }
  public JdbcInsertPolicy getInsertPolicy() { return insertPolicy; }
  public void setInsertPolicy(JdbcInsertPolicy value) { insertPolicy = Objects.requireNonNull(value); }
}
