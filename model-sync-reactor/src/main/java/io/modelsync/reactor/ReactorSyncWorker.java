package io.modelsync.reactor;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import java.time.Duration;
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
  private final Duration poll;
  private volatile boolean running;
  private Disposable subscription;

  public ReactorSyncWorker(
      SyncCoordinator coordinator,
      Scheduler persistence,
      Scheduler blockingOwner,
      int concurrency,
      Duration poll) {
    if (concurrency < 1 || concurrency > 128 || poll.isZero() || poll.isNegative())
      throw new IllegalArgumentException("Invalid worker bounds");
    this.coordinator = coordinator;
    this.persistence = persistence;
    this.blockingOwner = blockingOwner;
    this.concurrency = concurrency;
    this.poll = poll;
  }

  public Mono<Boolean> tick() {
    return Mono.fromCallable(coordinator::claim)
        .subscribeOn(persistence)
        .flatMap(
            claim -> {
              if (claim.isEmpty()) return Mono.just(false);
              WorkLease lease = claim.get();
              return Mono.fromCallable(() -> coordinator.prepare(lease))
                  .subscribeOn(persistence)
                  .flatMap(
                      plan -> {
                        if (plan.isEmpty()) return Mono.just(true);
                        PreparedSync<?> p = plan.get();
                        return fetch(p)
                            .switchIfEmpty(Mono.error(new Permanent("NULL_FETCH")))
                            .timeout(coordinator.options().fetchTimeout())
                            .flatMap(
                                result ->
                                    Mono.fromRunnable(() -> coordinator.finish(p, result))
                                        .subscribeOn(persistence)
                                        .thenReturn(true));
                      })
                  .onErrorResume(
                      error ->
                          Mono.fromRunnable(() -> coordinator.failed(lease, error))
                              .subscribeOn(persistence)
                              .thenReturn(true));
            });
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
                lane ->
                    Mono.defer(this::tick)
                        .onErrorResume(
                            error -> {
                              log.warn(
                                  "Model Sync infrastructure error: {}",
                                  error.getClass().getSimpleName());
                              return Mono.just(false);
                            })
                        .delayElement(poll)
                        .repeat(),
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
