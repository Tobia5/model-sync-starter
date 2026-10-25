package io.modelsync.reactor;

import java.time.*;
import java.util.concurrent.TimeUnit;
import reactor.core.scheduler.Scheduler;

/** The clock and virtual timer advance together; no sleep or wall-clock race. */
final class SchedulerClock extends Clock {
  private final Scheduler scheduler;
  private final ZoneId zone;
  SchedulerClock(Scheduler scheduler) { this(scheduler, ZoneOffset.UTC); }
  private SchedulerClock(Scheduler scheduler, ZoneId zone) { this.scheduler = scheduler; this.zone = zone; }
  public ZoneId getZone() { return zone; }
  public Clock withZone(ZoneId zone) { return new SchedulerClock(scheduler, zone); }
  public Instant instant() { return Instant.ofEpochMilli(scheduler.now(TimeUnit.MILLISECONDS)); }
}
