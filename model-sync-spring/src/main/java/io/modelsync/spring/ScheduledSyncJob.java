package io.modelsync.spring;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.application.SyncJob;
import io.modelsync.core.SyncJobResult;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/** Opt-in cron runner with an owned single-thread scheduler, not global @EnableScheduling. */
public final class ScheduledSyncJob implements SmartLifecycle {
  private static final System.Logger LOG = System.getLogger(ScheduledSyncJob.class.getName());
  private final SyncJob job;
  private final CronTrigger trigger;
  private final int maxItems;
  private final Duration maxDuration;
  private ThreadPoolTaskScheduler scheduler;
  private volatile boolean running;

  public ScheduledSyncJob(SyncJob job, String cron, ZoneId zone, int maxItems, Duration maxDuration) {
    this.job = Objects.requireNonNull(job, "job");
    this.trigger = new CronTrigger(Objects.requireNonNull(cron, "cron"), Objects.requireNonNull(zone, "zone"));
    this.maxItems = maxItems;
    this.maxDuration = Objects.requireNonNull(maxDuration, "maxDuration");
    if (maxItems < 1 || maxDuration.isNegative() || maxDuration.isZero()) throw new IllegalArgumentException("Invalid job limits");
    maxDuration.toNanos();
  }

  @Override
  public synchronized void start() {
    if (running) return;
    ThreadPoolTaskScheduler created = new ThreadPoolTaskScheduler();
    created.setPoolSize(1);
    created.setThreadNamePrefix("model-sync-job-");
    created.setRemoveOnCancelPolicy(true);
    created.setAwaitTerminationSeconds(30);
    created.initialize();
    try {
      var scheduled = created.schedule(() -> {
        try {
          SyncJobResult result = job.run(maxItems, maxDuration);
          LOG.log(System.Logger.Level.DEBUG, "Model Sync job attempted={0}, stop={1}", result.attempted(), result.stopReason());
        } catch (RuntimeException failure) {
          // Do not log payloads, tenant IDs, HTTP response bodies or exception messages.
          LOG.log(System.Logger.Level.ERROR, "Model Sync job infrastructure failure: {0}", failure.getClass().getSimpleName());
        }
      }, trigger);
      if (scheduled == null) throw new IllegalArgumentException("Cron has no future execution");
      scheduler = created;
      running = true;
    } catch (RuntimeException failure) {
      created.shutdown();
      throw failure;
    }
  }

  @Override
  public synchronized void stop() {
    running = false;
    if (scheduler != null) { scheduler.shutdown(); scheduler = null; }
  }
  @Override public boolean isRunning() { return running; }
  @Override public boolean isAutoStartup() { return true; }
  @Override public int getPhase() { return Integer.MAX_VALUE - 100; }
}
