package io.modelsync.core;

import static io.modelsync.core.SyncExceptions.*;

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
  private final DoubleSupplier jitter;

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
    this.jitter = Objects.requireNonNull(jitter);
  }

  public Optional<WorkLease> claim() {
    if (tx.active())
      throw new IllegalStateException("Worker cannot run inside a caller transaction");
    Priority first = priorities.next();
    return tx.required(
        () -> {
          Optional<WorkLease> result = store.claim(first, clock.instant(), options.lease());
          if (result.isPresent()) return result;
          for (Priority p : Priority.values())
            if (p != first) {
              result = store.claim(p, clock.instant(), options.lease());
              if (result.isPresent()) return result;
            }
          return Optional.empty();
        });
  }

  public Optional<PreparedSync<?>> prepare(WorkLease lease) {
    return prepareTyped(lease, registry.get(lease.key().binding()));
  }

  private <T> Optional<PreparedSync<?>> prepareTyped(WorkLease lease, ModelBinding<T> b) {
    Optional<PreparedSync<?>> plan =
        tx.required(
            () -> {
              Instant now = clock.instant();
              if (!store.lockValid(lease, now)) throw new LeaseLost();
              SyncState state = store.state(lease.key());
              Projection<T> local = b.projection().load(lease.key().subject()).orElse(null);
              boolean exact =
                  local != null
                      && !local.expired(now)
                      && state.matched()
                      && Objects.equals(b.digest(local.value()), state.matchedDigest());
              if (!lease.force()
                  && exact
                  && b.freshness().age(state.validatedAt(), now) == FreshnessPolicy.Age.FRESH) {
                // Do not renew age just because somebody read the cached data.
                store.complete(
                    lease, state, state.validatedAt().plus(b.freshness().refreshAfter()), now);
                return Optional.empty();
              }
              String validator = exact ? state.validator() : null;
              return Optional.of(
                  new PreparedSync<T>(
                      lease, b, local, state, new ReadRequest(lease.key().subject(), validator)));
            });
    if (plan.isPresent()) {
      QuotaGate.Admission admission = gate.acquire(b.quota());
      if (!admission.granted()) throw new RetryLater(admission.retryAt());
    }
    return plan;
  }

  public void finish(PreparedSync<?> prepared, FetchResult<?> result) {
    finishCast(prepared, result);
  }

  @SuppressWarnings("unchecked")
  private <T> void finishCast(PreparedSync<T> p, FetchResult<?> result) {
    if (result == null) throw new Permanent("NULL_FETCH");
    finishTyped(p, (FetchResult<T>) result);
  }

  private <T> void finishTyped(PreparedSync<T> p, FetchResult<T> result) {
    tx.required(
        () -> {
          Instant now = clock.instant();
          if (!store.lockValid(p.lease(), now)) throw new LeaseLost();
          ModelBinding<T> b = p.binding();
          SubjectKey key = p.lease().key().subject();
          Projection<T> current = b.projection().load(key).orElse(null);
          String before = p.local() == null ? null : p.local().revision();
          String revision = current == null ? null : current.revision();
          // Do not apply a response against a changed local snapshot.
          if (!Objects.equals(before, revision)) {
            store.reschedule(p.lease(), now, "LOCAL_CONFLICT", false, options.maxAttempts(), now);
            return null;
          }
          SyncState previous = p.previous();
          String outcome;
          String digest = previous.matchedDigest();
          String validator = result.validator();
          Instant matched = previous.reconciledAt();
          Instant changed = result.sourceChangedAt();
          if (result.status() == FetchResult.Status.ABSENT) {
            outcome = "OWNER_ABSENT";
            validator = null; // Quarantine, never implicit deletion.
          } else {
            T value;
            if (result.status() == FetchResult.Status.NOT_MODIFIED) {
              if (p.request().validator() == null
                  || current == null
                  || !previous.matched()
                  || !Objects.equals(b.digest(current.value()), previous.matchedDigest()))
                throw new Permanent("INVALID_NOT_MODIFIED");
              value = current.value();
              if (validator == null) validator = previous.validator();
              if (changed == null) changed = previous.sourceChangedAt();
            } else {
              value = b.modelType().cast(result.value());
            }
            String newDigest = b.digest(value);
            boolean equal = current != null && Objects.equals(b.digest(current.value()), newDigest);
            boolean touch = equal && b.role() == LocalRole.CACHE && b.repair();
            if ((!equal && b.repair()) || touch) {
              Instant expires =
                  b.role() == LocalRole.CACHE ? now.plus(b.freshness().retention()) : null;
              if (!b.projection().compareAndSet(key, revision, value, expires)) {
                store.reschedule(
                    p.lease(), now, "LOCAL_CONFLICT", false, options.maxAttempts(), now);
                return null;
              }
              equal = true;
              outcome =
                  result.status() == FetchResult.Status.NOT_MODIFIED ? "NOT_MODIFIED" : "APPLIED";
            } else {
              outcome =
                  equal
                      ? (result.status() == FetchResult.Status.NOT_MODIFIED
                          ? "NOT_MODIFIED"
                          : "MATCH")
                      : "MISMATCH";
            }
            if (equal) {
              matched = now;
              digest = newDigest;
            }
          }
          SyncState next =
              new SyncState(p.lease().key(), now, matched, digest, validator, outcome, changed);
          store.complete(p.lease(), next, now.plus(b.freshness().refreshAfter()), now);
          return null;
        });
  }

  public void failed(WorkLease lease, Throwable failure) {
    Throwable e = unwrap(failure);
    if (e instanceof LeaseLost) return;
    Instant now = clock.instant(), next;
    boolean count = true;
    String code;
    if (e instanceof RateLimitedAfter r) {
      next = now.plus(r.delay().isZero() ? Duration.ofMillis(1) : r.delay());
      count = false;
      code = "DEFERRED";
      gate.cooldown(registry.get(lease.key().binding()).quota(), next);
    } else if (e instanceof RetryLater r) {
      next = r.retryAt().isAfter(now) ? r.retryAt() : now.plusMillis(1);
      count = false;
      code = "DEFERRED";
      if (e instanceof RateLimited)
        gate.cooldown(registry.get(lease.key().binding()).quota(), next);
    } else {
      long cap = options.retryMax().toMillis();
      long factor = 1L << Math.min(lease.attempts(), 20);
      double delay = Math.min((double) cap, (double) options.retryBase().toMillis() * factor);
      next =
          now.plusMillis(
              Math.max(
                  1,
                  (long) (delay * (0.5 + 0.5 * Math.max(0, Math.min(1, jitter.getAsDouble()))))));
      code =
          e instanceof Permanent p
              ? p.code()
              : (e instanceof TimeoutException ? "FETCH_TIMEOUT" : "FETCH_FAILED");
    }
    final Instant when = next;
    final boolean isFailure = count;
    final int max = e instanceof Permanent ? 1 : options.maxAttempts();
    tx.required(
        () -> {
          if (store.lockValid(lease, clock.instant()))
            store.reschedule(lease, when, code, isFailure, max, clock.instant());
          return null;
        });
  }

  private static Throwable unwrap(Throwable e) {
    while ((e instanceof CompletionException || e instanceof ExecutionException)
        && e.getCause() != null) e = e.getCause();
    return e;
  }

  /** Headless/blocking entry point. HTTP adapters must also enforce transport timeouts. */
  public boolean runOne() {
    Optional<WorkLease> claim = claim();
    if (claim.isEmpty()) return false;
    WorkLease lease = claim.get();
    try {
      Optional<PreparedSync<?>> p = prepare(lease);
      if (p.isPresent()) finish(p.get(), fetchBlocking(p.get()));
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      failed(lease, e);
    }
    return true;
  }

  private FetchResult<?> fetchBlocking(PreparedSync<?> p) throws Exception {
    if (p.binding().owner() instanceof BlockingOwner<?> owner) return owner.fetch(p.request());
    AsyncOwner<?> owner = (AsyncOwner<?>) p.binding().owner();
    CompletableFuture<? extends FetchResult<?>> future =
        owner.fetchAsync(p.request()).toCompletableFuture();
    try {
      return future.get(options.fetchTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException | InterruptedException e) {
      future.cancel(true);
      throw e;
    }
  }

  public SyncOptions options() {
    return options;
  }
}
