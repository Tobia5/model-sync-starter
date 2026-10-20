package io.modelsync.core.domain;

import java.time.Instant;

/**
 * Latest known reconciliation state for a work key.
 *
 * @param key work key
 * @param validatedAt last successful authoritative response time
 * @param reconciledAt last time local mapped content was confirmed matching or repaired
 * @param matchedDigest digest of the local content confirmed at {@code reconciledAt}
 * @param validator latest owner validator
 * @param outcome latest reconciliation outcome code
 * @param sourceChangedAt owner-supplied source-change timestamp, if available
 */
public record SyncState(
    WorkKey key,
    Instant validatedAt,
    Instant reconciledAt,
    String matchedDigest,
    String validator,
    String outcome,
    Instant sourceChangedAt) {
  /**
   * Creates an empty state for a work key.
   *
   * @param key work key
   * @return empty synchronization state
   */
  public static SyncState empty(WorkKey key) {
    return new SyncState(key, null, null, null, null, "UNKNOWN", null);
  }

  /**
   * Returns whether the latest outcome represents confirmed local/source equality.
   *
   * @return {@code true} when the latest outcome is {@code MATCH}, {@code APPLIED}, or {@code
   *     NOT_MODIFIED}
   */
  public boolean matched() {
    return outcome.equals("MATCH") || outcome.equals("APPLIED") || outcome.equals("NOT_MODIFIED");
  }
}
