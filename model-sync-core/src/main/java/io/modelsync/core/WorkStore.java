package io.modelsync.core;

import java.time.*;
import java.util.Optional;

/**
 * Durable work-ledger port.
 *
 * <p>Methods run inside {@link LocalTransactions}. Completion follows {@link #lockValid(WorkLease,
 * Instant)} in the same transaction so reclaimed leases cannot complete old work.
 */
public interface WorkStore {
  /**
   * Enqueues or coalesces work for a key.
   *
   * @param key work key
   * @param priority requested priority
   * @param force whether to bypass normal freshness checks
   * @param now enqueue time
   * @return durable ticket identifier
   */
  String enqueue(WorkKey key, Priority priority, boolean force, Instant now);

  /**
   * Coalesces a visit refresh without resetting retries/cooldowns, reviving DEAD work, or
   * adding a new generation to an in-flight request. Empty means refresh suppressed (DEAD).
   * Custom stores must implement these stronger semantics before exposing age-on-visit.
   */
  default Optional<String> enqueueRefresh(WorkKey key, Priority priority, Instant now) {
    throw new UnsupportedOperationException("WorkStore does not support safe visit admission");
  }

  /**
   * Claims ready work for a priority bucket.
   *
   * @param priority priority bucket
   * @param now claim time
   * @param leaseTime lease duration
   * @return claimed lease when ready work exists
   */
  Optional<WorkLease> claim(Priority priority, Instant now, Duration leaseTime);

  /**
   * Validates that a lease still owns the durable work row.
   *
   * @param lease claimed lease
   * @param now validation time
   * @return {@code true} only when the lease token is current and unexpired
   */
  boolean lockValid(WorkLease lease, Instant now);

  /**
   * Loads the latest synchronization state for a work key.
   *
   * @param key work key
   * @return latest state
   */
  SyncState state(WorkKey key);

  /**
   * Completes a lease and stores the latest synchronization state.
   *
   * @param lease current lease
   * @param state state to persist
   * @param nextAt next scheduled work time
   * @param now completion time
   */
  void complete(WorkLease lease, SyncState state, Instant nextAt, Instant now);

  /**
   * Capability check used during coordinator construction. Existing custom stores remain
   * compatible with PERIODIC bindings, but must explicitly implement idle completion to opt in.
   */
  default boolean supportsOnDemand() {
    return false;
  }

  /**
   * Completes without scheduling a periodic refresh. A newer request generation must remain
   * WAITING, not be parked. Requires the same lease lock and transaction as complete().
   */
  default void completeOnDemand(WorkLease lease, SyncState state, Instant now) {
    throw new UnsupportedOperationException("WorkStore does not support on-demand completion");
  }

  /**
   * Reschedules a lease after quota denial, cooldown, or failure.
   *
   * @param lease current lease
   * @param nextAt next scheduled work time
   * @param errorCode stable diagnostic code
   * @param failure whether the reschedule counts as a failed attempt
   * @param maxAttempts maximum attempts before dead-lettering
   * @param now reschedule time
   */
  void reschedule(
      WorkLease lease,
      Instant nextAt,
      String errorCode,
      boolean failure,
      int maxAttempts,
      Instant now);
}
