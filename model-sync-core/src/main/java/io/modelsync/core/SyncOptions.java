package io.modelsync.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Coordinator runtime options.
 *
 * @param lease claimed-work lease duration
 * @param fetchTimeout timeout applied to owner fetches
 * @param retryBase base delay for exponential retry
 * @param retryMax maximum retry delay
 * @param maxAttempts maximum failed attempts before work is marked dead
 */
public record SyncOptions(
    Duration lease, Duration fetchTimeout, Duration retryBase, Duration retryMax, int maxAttempts) {
  public SyncOptions {
    for (Duration d : new Duration[] {lease, fetchTimeout, retryBase, retryMax})
      if (Objects.requireNonNull(d).isNegative() || d.isZero())
        throw new IllegalArgumentException("Positive duration required");
    if (lease.compareTo(fetchTimeout.multipliedBy(2)) <= 0)
      throw new IllegalArgumentException("Lease must exceed twice fetchTimeout");
    if (retryBase.compareTo(retryMax) > 0 || maxAttempts < 1)
      throw new IllegalArgumentException("Invalid retry policy");
  }

  /**
   * Returns default coordinator options.
   *
   * @return default options suitable for demos and tests
   */
  public static SyncOptions defaults() {
    return new SyncOptions(
        Duration.ofSeconds(60),
        Duration.ofSeconds(15),
        Duration.ofSeconds(2),
        Duration.ofMinutes(10),
        8);
  }
}
