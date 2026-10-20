package io.modelsync.core;

import java.time.Instant;

public final class RateLimitedAfter extends RuntimeException {
  private final java.time.Duration delay;

  /**
   * Creates a relative rate-limit exception.
   *
   * @param delay cooldown duration
   */
  public RateLimitedAfter(java.time.Duration delay) {
    super("Provider cooldown");
    this.delay = java.util.Objects.requireNonNull(delay);
    if (delay.isNegative()) throw new IllegalArgumentException("Negative cooldown");
  }

  /**
   * Returns the cooldown duration.
   *
   * @return cooldown duration
   */
  public java.time.Duration delay() {
    return delay;
  }
}
