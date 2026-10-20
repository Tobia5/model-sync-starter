package io.modelsync.core;

import java.time.Instant;
import java.util.Optional;

/**
 * Local projection storage port.
 *
 * <p>Implementations must participate in the caller's {@link LocalTransactions} boundary and must
 * not perform external owner calls.
 *
 * @param <T> synchronized model type
 */
public interface ProjectionStore<T> {
  /**
   * Loads a projection by subject key.
   *
   * @param key subject key
   * @return local projection when present
   */
  Optional<Projection<T>> load(SubjectKey key);

  /**
   * Atomically compares and writes a projection in the caller's local transaction.
   *
   * @param key subject key
   * @param expectedRevision expected current revision, or {@code null} for insert-if-absent
   * @param value replacement value
   * @param expiresAt physical cache expiry time, or {@code null}
   * @return {@code true} when the write succeeded; {@code false} on concurrent modification
   */
  boolean compareAndSet(SubjectKey key, String expectedRevision, T value, Instant expiresAt);
}
