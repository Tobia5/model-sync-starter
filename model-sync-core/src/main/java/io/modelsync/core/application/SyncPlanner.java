package io.modelsync.core.application;
import io.modelsync.core.port.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.DoubleSupplier;

/** One responsibility of the synchronization application service. */
final class SyncPlanner {
  private final ModelRegistry registry;
  private final WorkStore store;
  private final LocalTransactions tx;
  private final QuotaGate gate;
  private final Clock clock;
  private final SyncOptions options;
  private final SyncCompletion completion;
  SyncPlanner(ModelRegistry registry, WorkStore store, LocalTransactions tx, QuotaGate gate, Clock clock, SyncOptions options, SyncCompletion completion) {
    this.registry=registry; this.store=store; this.tx=tx; this.gate=gate; this.clock=clock; this.options=options; this.completion=completion;
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
                  && b.freshness().age(state.validatedAt(), now) == FreshnessAge.FRESH) {
                // Do not renew age just because somebody read the cached data.
                completion.complete(b, lease, state, state.validatedAt().plus(b.freshness().refreshAfter()), now);
                return Optional.empty();
              }
              String validator = exact ? state.validator() : null;
              return Optional.of(
                  new PreparedSync<T>(
                      lease, b, local, state, new ReadRequest(lease.key().subject(), validator)));
            });
    if (plan.isPresent()) {
      QuotaAdmission admission = gate.acquire(b.quota());
      if (!admission.granted()) throw new RetryLater(admission.retryAt());
    }
    return plan;
  }
}
