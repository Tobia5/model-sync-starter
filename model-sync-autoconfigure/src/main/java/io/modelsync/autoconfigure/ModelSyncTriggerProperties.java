package io.modelsync.autoconfigure;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Optional trigger adapters. Recurrence remains a per-binding decision. */
@ConfigurationProperties("model-sync.triggers")
public final class ModelSyncTriggerProperties {
  private boolean eventsEnabled;
  private final TriggerJobProperties job = new TriggerJobProperties();
  public boolean isEventsEnabled() { return eventsEnabled; }
  public void setEventsEnabled(boolean value) { eventsEnabled = value; }
  public TriggerJobProperties getJob() { return job; }

}
