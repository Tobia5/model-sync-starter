package io.modelsync.reactor;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import java.time.Duration;
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
  private final Duration poll, productiveDelay, maximumIdle;
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
    if (concurrency < 1 || concurrency > 128)
      throw new IllegalArgumentException("Invalid worker bounds");
    new WorkerPacing(productiveDelay, poll, maximumIdle);
    this.coordinator = coordinator;
    this.persistence = persistence;
    this.blockingOwner = blockingOwner;
    this.concurrency = concurrency;
    this.poll = poll;
    this.productiveDelay = productiveDelay;
    this.maximumIdle = maximumIdle;
  }

  public Mono<Boolean> tick() {
    return attempt().map(result -> result != WorkerTickResult.EMPTY);
  }

  private Mono<WorkerTickResult> attempt() {
    return Mono.fromCallable(coordinator::claim)
        .subscribeOn(persistence)
        .flatMap(
            claim -> {
              if (claim.isEmpty()) return Mono.just(WorkerTickResult.EMPTY);
              WorkLease lease = claim.get();
              return Mono.fromCallable(() -> coordinator.prepare(lease))
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
                                    Mono.fromRunnable(() -> coordinator.finish(p, result))
                                        .subscribeOn(persistence)
                                        .thenReturn(WorkerTickResult.FETCHED));
                      })
                  .onErrorResume(
                      error ->
                          Mono.fromRunnable(() -> coordinator.failed(lease, error))
                              .subscribeOn(persistence)
                              .thenReturn(error instanceof RetryLater ? WorkerTickResult.DEFERRED : WorkerTickResult.FAILED));
            });
  }

  Flux<WorkerTickResult> lane() {
    WorkerPacing pacing = new WorkerPacing(productiveDelay, poll, maximumIdle);
    return Mono.defer(() -> {
          ticks.incrementAndGet();
          maximumInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
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
        });
        })
        .flatMap(result -> Mono.delay(pacing.after(result)).thenReturn(result))
        .repeat();
  }

  /** A live snapshot; take interval endpoints after drain for exact scheduling accounting. */
  public WorkerActivity activity() {
    return new WorkerActivity(ticks.get(), empty.get(), deferred.get(), failed.get(), fetched.get(),
        cancelled.get(), inFlight.get(), maximumInFlight.get());
  }

  private Mono<FetchResult<?>> fetch(PreparedSync<?> prepared) {
    if (prepared.binding().owner() instanceof BlockingOwner<?> owner) {
      return Mono.<FetchResult<?>>fromCallable(() -> owner.fetch(prepared.request()))
          .subscribeOn(blockingOwner);
    }
    AsyncOwner<?> owner = (AsyncOwner<?>) prepared.binding().owner();
    return Mono.defer(
        () ->
            Mono.fromFuture(owner.fetchAsync(prepared.request()).toCompletableFuture())
                .map(result -> (FetchResult<?>) result));
  }

  @Override
  public synchronized void start() {
    if (running) return;
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
    running = false;
    if (subscription != null) subscription.dispose();
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
