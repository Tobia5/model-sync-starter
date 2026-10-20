package io.modelsync.core;

import java.time.Instant;

public class RetryLater extends RuntimeException {
  private final Instant retryAt;

  /**
   * Creates a retry-later exception.
   *
   * @param retryAt earliest retry instant
   */
  public RetryLater(Instant retryAt) {
    super("Deferred by admission policy");
    this.retryAt = java.util.Objects.requireNonNull(retryAt);
  }

  /**
   * Returns the earliest retry instant.
   *
   * @return retry instant
   */
  public Instant retryAt() {
    return retryAt;
  }
}
