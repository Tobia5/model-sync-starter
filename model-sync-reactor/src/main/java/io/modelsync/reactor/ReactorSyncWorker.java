package io.modelsync.reactor;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import reactor.core.Disposable;
import reactor.core.publisher.*;
import reactor.core.scheduler.Scheduler;

/** Bounded execution lanes. At most concurrency leases are admitted by this instance. */
public final class ReactorSyncWorker implements SmartLifecycle {
  private static final Logger log = LoggerFactory.getLogger(ReactorSyncWorker.class);
  private final SyncCoordinator coordinator;
  private final Scheduler persistence, blockingOwner;
  private final int concurrency;
  private final Duration poll, productiveDelay, maximumIdle, shutdownTimeout;
  private final Object operations = new Object();
  private final AtomicInteger physical = new AtomicInteger();
  private final Semaphore ownerCapacity;
  private volatile boolean stopping;
  private volatile WorkerShutdown shutdown = new WorkerShutdown(WorkerShutdownStatus.NOT_STOPPED, 0, 0, 0, 0);
  private volatile boolean running;
  private Disposable subscription;
  private final AtomicLong ticks = new AtomicLong(), empty = new AtomicLong(),
      deferred = new AtomicLong(), failed = new AtomicLong(), fetched = new AtomicLong(),
      cancelled = new AtomicLong();
  private final AtomicInteger inFlight = new AtomicInteger(), maximumInFlight = new AtomicInteger();

  public ReactorSyncWorker(
      SyncCoordinator coordinator,
      Scheduler persistence,
      Scheduler blockingOwner,
      int concurrency,
      Duration poll) {
    this(coordinator, persistence, blockingOwner, concurrency, poll, poll, poll);
  }

  /** Explicit pacing. The five-argument constructor retains its fixed-delay behavior. */
  public ReactorSyncWorker(
      SyncCoordinator coordinator, Scheduler persistence, Scheduler blockingOwner,
      int concurrency, Duration poll, Duration productiveDelay, Duration maximumIdle) {
    this(coordinator, persistence, blockingOwner, concurrency, poll, productiveDelay,
        maximumIdle, Duration.ofSeconds(5));
  }

  /** Drain bound is independent of fetch, transport, database and transaction deadlines. */
  public ReactorSyncWorker(
      SyncCoordinator coordinator, Scheduler persistence, Scheduler blockingOwner,
      int concurrency, Duration poll, Duration productiveDelay, Duration maximumIdle,
      Duration shutdownTimeout) {
    if (concurrency < 1 || concurrency > 128)
      throw new IllegalArgumentException("Invalid worker bounds");
    new WorkerPacing(productiveDelay, poll, maximumIdle);
    if (shutdownTimeout == null || shutdownTimeout.isNegative() || shutdownTimeout.isZero()
        || shutdownTimeout.compareTo(Duration.ofMinutes(5)) > 0)
      throw new IllegalArgumentException("shutdownTimeout must be positive and at most five minutes");
    this.coordinator = coordinator;
    this.persistence = persistence;
    this.blockingOwner = blockingOwner;
    this.concurrency = concurrency;
    this.poll = poll;
    this.productiveDelay = productiveDelay;
    this.maximumIdle = maximumIdle;
    this.shutdownTimeout = shutdownTimeout;
    this.ownerCapacity = new Semaphore(concurrency);
  }

  public Mono<Boolean> tick() {
    return trackedAttempt().map(result -> result != WorkerTickResult.EMPTY);
  }

  private Mono<WorkerTickResult> attempt() {
    return Mono.fromCallable(this::claim)
        .subscribeOn(persistence)
        .flatMap(
            claim -> {
              if (claim.isEmpty()) return Mono.just(WorkerTickResult.EMPTY);
              WorkLease lease = claim.get();
              return Mono.fromCallable(() -> physical(() -> coordinator.prepare(lease), false))
                  .subscribeOn(persistence)
                  .flatMap(
                      plan -> {
                        if (plan.isEmpty()) return Mono.just(WorkerTickResult.DEFERRED);
                        PreparedSync<?> p = plan.get();
                        return fetch(p)
                            .switchIfEmpty(Mono.error(new Permanent("NULL_FETCH")))
                            .timeout(coordinator.options().fetchTimeout())
                            .flatMap(
                                result ->
                                    Mono.fromCallable(() -> physical(() -> { coordinator.finish(p, result); return true; }, false))
                                        .subscribeOn(persistence)
                                        .thenReturn(WorkerTickResult.FETCHED));
                      })
                  .onErrorResume(
                      error ->
                          Mono.fromCallable(() -> physical(() -> { coordinator.failed(lease, error); return true; }, false))
                              .subscribeOn(persistence)
                              .thenReturn(error instanceof RetryLater ? WorkerTickResult.DEFERRED : WorkerTickResult.FAILED));
            });
  }

  private Mono<WorkerTickResult> trackedAttempt() {
    return Mono.defer(() -> {
          synchronized (operations) {
            if (stopping) return Mono.just(WorkerTickResult.EMPTY);
          ticks.incrementAndGet();
          maximumInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
          }
          return attempt()
        .onErrorResume(error -> {
          log.warn("Model Sync infrastructure error: {}", error.getClass().getSimpleName());
          return Mono.just(WorkerTickResult.FAILED);
        })
        .doOnNext(result -> {
          if (result == WorkerTickResult.EMPTY) empty.incrementAndGet();
          else if (result == WorkerTickResult.DEFERRED) deferred.incrementAndGet();
          else if (result == WorkerTickResult.FAILED) failed.incrementAndGet();
          else fetched.incrementAndGet();
        })
        .doFinally(signal -> {
          inFlight.decrementAndGet();
          if (signal == SignalType.CANCEL) cancelled.incrementAndGet();
          synchronized (operations) { operations.notifyAll(); }
        });
        });
  }

  Flux<WorkerTickResult> lane() {
    WorkerPacing pacing = new WorkerPacing(productiveDelay, poll, maximumIdle);
    return Mono.defer(this::trackedAttempt)
        .flatMap(result -> Mono.delay(pacing.after(result)).thenReturn(result))
        .repeat(() -> !stopping);
  }

  /** A live snapshot; take interval endpoints after drain for exact scheduling accounting. */
  public WorkerActivity activity() {
    return new WorkerActivity(ticks.get(), empty.get(), deferred.get(), failed.get(), fetched.get(),
        cancelled.get(), inFlight.get(), maximumInFlight.get());
  }

  private Mono<FetchResult<?>> fetch(PreparedSync<?> prepared) {
    if (prepared.binding().owner() instanceof BlockingOwner<?> owner) {
      return Mono.<FetchResult<?>>fromCallable(() -> physical(() -> owner.fetch(prepared.request()), true))
          .subscribeOn(blockingOwner);
    }
    AsyncOwner<?> owner = (AsyncOwner<?>) prepared.binding().owner();
    return Mono.defer(() -> {
      if (!ownerCapacity.tryAcquire()) return Mono.error(new RetryLater(Instant.now().plus(poll)));
      physical.incrementAndGet();
      try {
        var future = owner.fetchAsync(prepared.request()).toCompletableFuture();
        future.whenComplete((value, error) -> { ownerCapacity.release(); physicalDone(); });
        // Cancelling a future can mark it complete while its transport is still active.
        // Observe the owner's actual stage completion; never apply a cancelled late result.
        return Mono.fromFuture(future, true).map(result -> (FetchResult<?>) result);
      } catch (Throwable error) {
        ownerCapacity.release(); physicalDone(); return Mono.error(error);
      }
    });
  }

  private Optional<WorkLease> claim() throws Exception {
    synchronized (operations) {
      if (stopping) return Optional.empty();
      physical.incrementAndGet();
    }
    try { return coordinator.claim(); }
    finally { physicalDone(); }
  }

  private <T> T physical(Callable<T> operation, boolean owner) throws Exception {
    if (owner && !ownerCapacity.tryAcquire()) throw new RetryLater(Instant.now().plus(poll));
    physical.incrementAndGet();
    try { return operation.call(); }
    finally {
      if (owner) ownerCapacity.release();
      physicalDone();
    }
  }

  private void physicalDone() {
    physical.decrementAndGet();
    synchronized (operations) { operations.notifyAll(); }
  }

  /** Live tracked callables/stages; an arbitrary owner must also define transport release. */
  public int physicalOperations() { return physical.get(); }

  /** Immutable snapshot taken at the last bounded stop decision. */
  public WorkerShutdown shutdown() { return shutdown; }

  @Override
  public synchronized void start() {
    if (running) return;
    if (inFlight.get() != 0 || physical.get() != 0)
      throw new IllegalStateException("Previous Model Sync operations have not drained");
    stopping = false;
    running = true;
    subscription =
        Flux.range(0, concurrency)
            .flatMap(
                lane -> lane(),
                concurrency)
            .subscribe(
                ignored -> {},
                error -> {
                  running = false;
                  log.error("Model Sync worker stopped: {}", error.getClass().getSimpleName());
                });
  }

  @Override
  public synchronized void stop() {
    long began = System.nanoTime();
    long remaining = shutdownTimeout.toNanos();
    synchronized (operations) {
      stopping = true;
      running = false;
      while ((inFlight.get() != 0 || physical.get() != 0) && remaining > 0) {
        try { TimeUnit.NANOSECONDS.timedWait(operations, remaining); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
        remaining = shutdownTimeout.toNanos() - (System.nanoTime() - began);
      }
      shutdown = new WorkerShutdown(inFlight.get() == 0 && physical.get() == 0
          ? WorkerShutdownStatus.DRAINED : WorkerShutdownStatus.TIMED_OUT,
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began), inFlight.get(), physical.get(),
          concurrency - ownerCapacity.availablePermits());
    }
    if (subscription != null) subscription.dispose();
    if (shutdown.status() == WorkerShutdownStatus.TIMED_OUT)
      log.warn("Model Sync stop timed out: logical={}, physical={}, owners={}",
          shutdown.logicalInFlight(), shutdown.physicalOperations(), shutdown.ownerOperations());
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    return Integer.MAX_VALUE - 100;
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }
}
