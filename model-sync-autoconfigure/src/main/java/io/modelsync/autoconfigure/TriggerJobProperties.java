package io.modelsync.autoconfigure;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

public final class TriggerJobProperties {
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
