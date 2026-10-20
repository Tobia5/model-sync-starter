package io.modelsync.core.application;
import io.modelsync.core.port.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.DoubleSupplier;

/** One responsibility of the synchronization application service. */
final class SyncResultApplier {
  private final WorkStore store;
  private final LocalTransactions tx;
  private final Clock clock;
  private final SyncOptions options;
  private final SyncCompletion completion;
  SyncResultApplier(WorkStore store, LocalTransactions tx, Clock clock, SyncOptions options, SyncCompletion completion) {
    this.store=store; this.tx=tx; this.clock=clock; this.options=options; this.completion=completion;
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
          if (result.status() == FetchStatus.ABSENT) {
            outcome = "OWNER_ABSENT";
            validator = null; // Quarantine, never implicit deletion.
          } else {
            T value;
            if (result.status() == FetchStatus.NOT_MODIFIED) {
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
                  result.status() == FetchStatus.NOT_MODIFIED ? "NOT_MODIFIED" : "APPLIED";
            } else {
              outcome =
                  equal
                      ? (result.status() == FetchStatus.NOT_MODIFIED
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
          completion.complete(b, p.lease(), next, now.plus(b.freshness().refreshAfter()), now);
          return null;
        });
  }
}
