package io.modelsync.core.application;
import io.modelsync.core.port.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.DoubleSupplier;

/** One responsibility of the synchronization application service. */
final class SyncFailurePolicy {
  private final ModelRegistry registry;
  private final WorkStore store;
  private final LocalTransactions tx;
  private final QuotaGate gate;
  private final Clock clock;
  private final SyncOptions options;
  private final DoubleSupplier jitter;
  SyncFailurePolicy(ModelRegistry registry, WorkStore store, LocalTransactions tx, QuotaGate gate, Clock clock, SyncOptions options, DoubleSupplier jitter) {
    this.registry=registry; this.store=store; this.tx=tx; this.gate=gate; this.clock=clock; this.options=options; this.jitter=jitter;
  }
  public void failed(WorkLease lease, Throwable failure) {
    if (tx.active())
      throw new IllegalStateException("Persist failure only after the attempt transaction has ended");
    Throwable e = unwrap(failure);
    if (e instanceof LeaseLost) return;
    Instant now = clock.instant(), next;
    boolean count = true;
    String code;
    if (e instanceof RateLimitedAfter r) {
      next = now.plus(r.delay().isZero() ? Duration.ofMillis(1) : r.delay());
      count = false;
      code = "DEFERRED";
      var receipt = gate.publishCooldown(new CooldownRequest(Digests.sha256Fields(lease.key().storageId(),lease.token()), registry.get(lease.key().binding()).quota(), next));
      next = receipt.request().until();
      if (receipt.state() == CooldownState.DURABLE_PENDING) code = "COOLDOWN_PENDING";
    } else if (e instanceof RetryLater r) {
      next = r.retryAt().isAfter(now) ? r.retryAt() : now.plusMillis(1);
      count = false;
      code = "DEFERRED";
      if (e instanceof RateLimited) {
        var receipt = gate.publishCooldown(new CooldownRequest(Digests.sha256Fields(lease.key().storageId(),lease.token()), registry.get(lease.key().binding()).quota(), next));
        next = receipt.request().until();
        if (receipt.state() == CooldownState.DURABLE_PENDING) code = "COOLDOWN_PENDING";
      }
    } else if (e instanceof CooldownUnavailable unavailable) {
      next = unavailable.retryAt().isAfter(now) ? unavailable.retryAt() : now.plusMillis(1);
      count = false;
      code = "COOLDOWN_UNRECORDED";
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
          e instanceof LocalContention ? "LOCAL_CONTENTION" : e instanceof Permanent p
              ? p.code()
              : (e instanceof TimeoutException ? "FETCH_TIMEOUT" : "FETCH_FAILED");
    }
    final Instant when = next;
    final boolean isFailure = count;
    final String errorCode = code;
    final int max = e instanceof Permanent ? 1 : options.maxAttempts();
    tx.required(
        () -> {
          if (store.lockValid(lease, clock.instant()))
            store.reschedule(lease, when, errorCode, isFailure, max, clock.instant());
          return null;
        });
  }

  private static Throwable unwrap(Throwable e) {
    while ((e instanceof CompletionException || e instanceof ExecutionException)
        && e.getCause() != null) e = e.getCause();
    return e;
  }
}
