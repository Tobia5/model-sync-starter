package io.modelsync.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
public record FreshnessPolicy(Duration refreshAfter, Duration maxAge, Duration retention) {
    public enum Age { UNKNOWN, FRESH, STALE, EXPIRED }
    public FreshnessPolicy {
        positive(refreshAfter); positive(maxAge); positive(retention);
        if (refreshAfter.compareTo(maxAge) > 0) throw new IllegalArgumentException("refreshAfter > maxAge");
    }
    private static void positive(Duration d) {
        Objects.requireNonNull(d);
        if (d.isZero() || d.isNegative()) throw new IllegalArgumentException("Duration must be positive");
    }
    public Age age(Instant validatedAt, Instant now) {
        Objects.requireNonNull(now);
        if (validatedAt == null || validatedAt.isAfter(now)) return Age.UNKNOWN;
        Duration age = Duration.between(validatedAt, now);
        if (age.compareTo(maxAge) >= 0) return Age.EXPIRED;
        return age.compareTo(refreshAfter) >= 0 ? Age.STALE : Age.FRESH;
    }
    public static FreshnessPolicy defaults() {
        return new FreshnessPolicy(Duration.ofDays(1), Duration.ofDays(7), Duration.ofDays(7));
    }
}
