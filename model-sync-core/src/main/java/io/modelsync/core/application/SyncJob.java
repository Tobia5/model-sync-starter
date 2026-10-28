package io.modelsync.core.application;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Bounded, sequential drain of due work. A run is not an inventory scan or a Spring Batch Job.
 * The time budget is checked BETWEEN attempts; transport timeouts must bound a blocking call.
 */
public final class SyncJob {

  private final SyncCoordinator coordinator;
  private final LongSupplier ticker;
  private final AtomicBoolean running = new AtomicBoolean();

  public SyncJob(SyncCoordinator coordinator) { this(coordinator, System::nanoTime); }

  /** Injectable monotonic ticker for deterministic tests, not a wall clock. */
  public SyncJob(SyncCoordinator coordinator, LongSupplier ticker) {
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    this.ticker = Objects.requireNonNull(ticker, "ticker");
  }

  public SyncJobResult run(int maxItems, Duration maxDuration) {
    if (maxItems < 1) throw new IllegalArgumentException("maxItems must be positive");
    Objects.requireNonNull(maxDuration, "maxDuration");
    if (maxDuration.isZero() || maxDuration.isNegative()) throw new IllegalArgumentException("maxDuration must be positive");
    final long budget = maxDuration.toNanos();
    if (!running.compareAndSet(false, true)) return new SyncJobResult(0, SyncJobStopReason.ALREADY_RUNNING);
    try {
      long start = ticker.getAsLong();
      int attempted = 0;
      while (attempted < maxItems) {
        if (Thread.currentThread().isInterrupted()) return new SyncJobResult(attempted, SyncJobStopReason.INTERRUPTED);
        if (ticker.getAsLong() - start >= budget) return new SyncJobResult(attempted, SyncJobStopReason.TIME_BUDGET);
        if (!coordinator.runOne()) return new SyncJobResult(attempted, SyncJobStopReason.EMPTY);
        attempted++;
      }
      return new SyncJobResult(attempted, SyncJobStopReason.ITEM_LIMIT);
    } finally {
      running.set(false);
    }
  }
}
