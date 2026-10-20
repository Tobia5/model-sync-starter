package io.modelsync.core;

import java.util.Objects;
import java.util.Optional;

/**
 * Local visit decision. An empty projection means pending/unavailable, not authoritative absence.
 * A refresh ticket identifies a durable subject work item, not an individual completed execution.
 */
public record VisitResult<T>(Optional<Projection<T>> projection, FreshnessPolicy.Age age,
                             Optional<String> refreshTicket) {
  public VisitResult {
    Objects.requireNonNull(projection, "projection");
    Objects.requireNonNull(age, "age");
    Objects.requireNonNull(refreshTicket, "refreshTicket");
    if (projection.isPresent() && age != FreshnessPolicy.Age.FRESH && age != FreshnessPolicy.Age.STALE) {
      throw new IllegalArgumentException("Unknown or expired projections cannot be served");
    }
  }
  public boolean refreshRequested() { return refreshTicket.isPresent(); }
  public boolean pending() { return projection.isEmpty() && refreshRequested(); }
  /** A stale/missing visit could not enqueue because the subject is quarantined. */
  public boolean refreshSuppressed() { return age != FreshnessPolicy.Age.FRESH && refreshTicket.isEmpty(); }
}
