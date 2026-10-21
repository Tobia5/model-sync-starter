package io.modelsync.reactor;

import java.time.Duration;
import java.util.Objects;

/** Per-lane bounded pacing; no queue/deadline cache or authorization of owner I/O. */
final class WorkerPacing {
  private final Duration productive;
  private final Duration idle;
  private final Duration maximumIdle;
  private Duration nextIdle;

  WorkerPacing(Duration productive, Duration idle, Duration maximumIdle) {
    for (Duration duration : new Duration[] {productive, idle, maximumIdle}) {
      Objects.requireNonNull(duration, "Worker delay");
      if (duration.isNegative() || duration.isZero())
        throw new IllegalArgumentException("Worker delays must be positive");
    }
    if (maximumIdle.compareTo(idle) < 0)
      throw new IllegalArgumentException("Maximum idle delay must cover poll interval");
    this.productive = productive;
    this.idle = idle;
    this.maximumIdle = maximumIdle;
    this.nextIdle = idle;
  }

  Duration after(WorkerTickResult result) {
    if (result == WorkerTickResult.FETCHED) {
      nextIdle = idle;
      return productive;
    }
    Duration delay = nextIdle;
    // Saturate without overflowing Duration, including extreme user-supplied bounds.
    nextIdle = nextIdle.compareTo(maximumIdle.minus(nextIdle)) >= 0
        ? maximumIdle : nextIdle.plus(nextIdle);
    return delay;
  }
}
