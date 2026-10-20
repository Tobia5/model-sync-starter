package io.modelsync.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Freshness thresholds for serving and scheduling local projections.
 *
 * @param refreshAfter age at which a verified projection should be refreshed
 * @param maxAge maximum age that local-only reads may serve
 * @param retention physical cache retention duration
 */
public record FreshnessPolicy(Duration refreshAfter, Duration maxAge, Duration retention) {
  /** Age classification for a local projection relative to this policy. */
  public enum Age {
    /** No trusted validation time or matching digest exists. */
    UNKNOWN,
    /** Projection is younger than {@link FreshnessPolicy#refreshAfter()}. */
    FRESH,
    /**
     * Projection is older than refresh target but still inside {@link FreshnessPolicy#maxAge()}.
     */
    STALE,
    /** Projection reached or exceeded the serving limit. */
    EXPIRED
  }

  public FreshnessPolicy {
    positive(refreshAfter);
    positive(maxAge);
    positive(retention);
    if (refreshAfter.compareTo(maxAge) > 0)
      throw new IllegalArgumentException("refreshAfter > maxAge");
  }

  private static void positive(Duration d) {
    Objects.requireNonNull(d);
    if (d.isZero() || d.isNegative())
      throw new IllegalArgumentException("Duration must be positive");
  }

  /**
   * Classifies a validation timestamp against this policy.
   *
   * @param validatedAt timestamp of the last owner validation, or {@code null}
   * @param now comparison instant
   * @return freshness age bucket
   */
  public Age age(Instant validatedAt, Instant now) {
    Objects.requireNonNull(now);
    if (validatedAt == null || validatedAt.isAfter(now)) return Age.UNKNOWN;
    Duration age = Duration.between(validatedAt, now);
    if (age.compareTo(maxAge) >= 0) return Age.EXPIRED;
    return age.compareTo(refreshAfter) >= 0 ? Age.STALE : Age.FRESH;
  }

  /**
   * Returns conservative default freshness settings.
   *
   * @return one-day refresh target with seven-day serving and retention limits
   */
  public static FreshnessPolicy defaults() {
    return new FreshnessPolicy(Duration.ofDays(1), Duration.ofDays(7), Duration.ofDays(7));
  }
}
