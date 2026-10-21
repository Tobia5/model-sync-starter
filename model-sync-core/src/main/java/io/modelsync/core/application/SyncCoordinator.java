package io.modelsync.core.application;
import io.modelsync.core.port.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.DoubleSupplier;

/** Shared plan/commit logic. No Spring, Reactor, JPA or HTTP dependencies. */
public final class SyncCoordinator {
  private final ModelRegistry registry;
  private final WorkStore store;
  private final LocalTransactions tx;
  private final QuotaGate gate;
  private final Clock clock;
  private final SyncOptions options;
  private final WeightedPriorities priorities;
  private final SyncPlanner planner;
  private final SyncResultApplier applier;
  private final SyncFailurePolicy failurePolicy;
  private final OwnerCallExecutor ownerCalls;
  private final ProviderClaimScheduler scheduling;

  public SyncCoordinator(
      ModelRegistry registry,
      WorkStore store,
      LocalTransactions tx,
      QuotaGate gate,
      Clock clock,
      SyncOptions options,
      WeightedPriorities priorities,
      DoubleSupplier jitter) {
    this.registry = Objects.requireNonNull(registry);
    this.store = Objects.requireNonNull(store);
    this.tx = Objects.requireNonNull(tx);
    this.gate = Objects.requireNonNull(gate);
    this.clock = Objects.requireNonNull(clock);
    this.options = Objects.requireNonNull(options);
    this.priorities = Objects.requireNonNull(priorities);
    Objects.requireNonNull(jitter);
    SyncCompletion completion = new SyncCompletion(store);
    this.planner = new SyncPlanner(registry, store, tx, gate, clock, options, completion);
    this.applier = new SyncResultApplier(store, tx, clock, options, completion);
    this.failurePolicy = new SyncFailurePolicy(registry, store, tx, gate, clock, options, jitter);
    this.ownerCalls = new OwnerCallExecutor(options);
    this.scheduling = new ProviderClaimScheduler(registry, gate);
    if (registry.all().stream().anyMatch(b -> b.refreshMode() == RefreshMode.ON_DEMAND)
        && !store.supportsOnDemand()) {
      throw new IllegalStateException("ON_DEMAND requires an idle-capable WorkStore; JDBC requires schema v2");
    }
  }

  public Optional<WorkLease> claim() {
    if (tx.active())
      throw new IllegalStateException("Worker cannot run inside a caller transaction");
    Instant now = clock.instant();
    ClaimSchedule schedule = scheduling.schedule(now, options.lease());
    Set<String> eligible = schedule.waitingBindings().orElse(null);
    Priority first = priorities.next();
    Optional<WorkLease> claimed = Optional.empty();
    try {
      claimed = tx.required(() -> {
        Optional<WorkLease> result = claimPriority(first, now, eligible);
        if (result.isPresent()) return result;
        for (Priority p : Priority.values()) {
          if (p != first) {
            result = claimPriority(p, now, eligible);
            if (result.isPresent()) return result;
          }
        }
        return Optional.empty();
      });
      return claimed;
    } finally {
      scheduling.claimed(schedule, claimed);
    }
  }

  private Optional<WorkLease> claimPriority(Priority priority, Instant now, Set<String> eligible) {
    return eligible == null ? store.claim(priority, now, options.lease())
        : store.claim(priority, now, options.lease(), eligible);
  }

  public Optional<PreparedSync<?>> prepare(WorkLease lease) {
    try { return planner.prepare(lease); }
    finally { scheduling.release(lease); }
  }
  public void finish(PreparedSync<?> prepared, FetchResult<?> result) { applier.finish(prepared, result); }
  public void failed(WorkLease lease, Throwable failure) {
    try { failurePolicy.failed(lease, failure); }
    finally { scheduling.release(lease); }
  }

  /** Headless/blocking entry point. HTTP adapters must also enforce transport timeouts. */
  public boolean runOne() {
    Optional<WorkLease> claim = claim();
    if (claim.isEmpty()) return false;
    WorkLease lease = claim.get();
    try {
      Optional<PreparedSync<?>> p = prepare(lease);
      if (p.isPresent()) finish(p.get(), ownerCalls.fetchBlocking(p.get()));
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      failed(lease, e);
    }
    return true;
  }

  public SyncOptions options() {
    return options;
  }
}
