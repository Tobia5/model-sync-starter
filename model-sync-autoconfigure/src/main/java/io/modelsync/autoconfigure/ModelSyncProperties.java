package io.modelsync.autoconfigure;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for Model Sync runtime, worker, retry, and quota settings. */
@ConfigurationProperties("model-sync")
public final class ModelSyncProperties {
  private boolean enabled = false;
  private boolean workerEnabled = false;
  private Duration lease = Duration.ofSeconds(60),
      fetchTimeout = Duration.ofSeconds(15),
      pollInterval = Duration.ofMillis(250);
  private Duration retryBase = Duration.ofSeconds(2), retryMax = Duration.ofMinutes(10);
  private int maxAttempts = 8,
      concurrency = 4,
      persistenceThreads = 4,
      ownerThreads = 8,
      queuedTasksPerThread = 64;
  private Map<String, Duration> quotas = new LinkedHashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean v) {
    enabled = v;
  }

  public boolean isWorkerEnabled() {
    return workerEnabled;
  }

  public void setWorkerEnabled(boolean v) {
    workerEnabled = v;
  }

  public Duration getLease() {
    return lease;
  }

  public void setLease(Duration v) {
    lease = v;
  }

  public Duration getFetchTimeout() {
    return fetchTimeout;
  }

  public void setFetchTimeout(Duration v) {
    fetchTimeout = v;
  }

  public Duration getPollInterval() {
    return pollInterval;
  }

  public void setPollInterval(Duration v) {
    pollInterval = v;
  }

  public Duration getRetryBase() {
    return retryBase;
  }

  public void setRetryBase(Duration v) {
    retryBase = v;
  }

  public Duration getRetryMax() {
    return retryMax;
  }

  public void setRetryMax(Duration v) {
    retryMax = v;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int v) {
    maxAttempts = v;
  }

  public int getConcurrency() {
    return concurrency;
  }

  public void setConcurrency(int v) {
    concurrency = v;
  }

  public int getPersistenceThreads() {
    return persistenceThreads;
  }

  public void setPersistenceThreads(int v) {
    persistenceThreads = v;
  }

  public int getOwnerThreads() {
    return ownerThreads;
  }

  public void setOwnerThreads(int v) {
    ownerThreads = v;
  }

  public int getQueuedTasksPerThread() {
    return queuedTasksPerThread;
  }

  public void setQueuedTasksPerThread(int v) {
    queuedTasksPerThread = v;
  }

  public Map<String, Duration> getQuotas() {
    return quotas;
  }

  public void setQuotas(Map<String, Duration> v) {
    quotas = v;
  }

  public void validateBounds() {
    if (persistenceThreads < 1
        || ownerThreads < 1
        || queuedTasksPerThread < 1
        || concurrency < 1
        || concurrency > 128)
      throw new IllegalArgumentException("Invalid Model Sync execution bounds");
    if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative())
      throw new IllegalArgumentException("pollInterval must be positive");
  }
}
