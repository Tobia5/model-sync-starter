package io.modelsync.autoconfigure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Optional trigger adapters. Recurrence remains a per-binding decision. */
@ConfigurationProperties("model-sync.triggers")
public final class ModelSyncTriggerProperties {
  private boolean eventsEnabled;
  private final Job job = new Job();
  public boolean isEventsEnabled() { return eventsEnabled; }
  public void setEventsEnabled(boolean value) { eventsEnabled = value; }
  public Job getJob() { return job; }

  public static final class Job {
    private boolean enabled;
    private String cron = "0 */5 * * * *";
    private String zone = "UTC";
    private int maxItems = 100;
    private Duration maxDuration = Duration.ofSeconds(30);
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getCron() { return cron; }
    public void setCron(String value) { cron = value; }
    public String getZone() { return zone; }
    public void setZone(String value) { zone = value; }
    public int getMaxItems() { return maxItems; }
    public void setMaxItems(int value) { maxItems = value; }
    public Duration getMaxDuration() { return maxDuration; }
    public void setMaxDuration(Duration value) { maxDuration = value; }
  }
}
