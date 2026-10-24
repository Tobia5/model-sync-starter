package io.modelsync.core;

import java.time.Instant;

public final class RateLimited extends RetryLater {
  /**
   * Creates an absolute rate-limit exception.
   *
   * @param t earliest retry instant
   */
  public RateLimited(Instant t) {
    super(t);
  }
}
