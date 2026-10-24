package io.modelsync.core.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable local projection snapshot.
 *
 * <p>The value should be a detached DTO plus an optimistic local revision. It is never a managed
 * JPA entity.
 *
 * @param value detached model value
 * @param revision local optimistic revision
 * @param expiresAt physical cache expiry time, or {@code null} when no physical expiry applies
 * @param <T> synchronized model type
 */
public record Projection<T>(T value, String revision, Instant expiresAt) {
  public Projection {
    Objects.requireNonNull(value);
    Objects.requireNonNull(revision);
  }

  /**
   * Tests whether the projection has reached its physical expiry instant.
   *
   * @param now comparison instant
   * @return {@code true} when {@code expiresAt} is non-null and not after {@code now}
   */
  public boolean expired(Instant now) {
    return expiresAt != null && !expiresAt.isAfter(now);
  }
}
