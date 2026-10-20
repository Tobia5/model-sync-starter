package io.modelsync.core;

import java.time.Instant;
import java.util.Objects;
/** Immutable DTO plus local optimistic revision; never a managed JPA entity. */
public record Projection<T>(T value, String revision, Instant expiresAt) {
    public Projection { Objects.requireNonNull(value); Objects.requireNonNull(revision); }
    public boolean expired(Instant now) { return expiresAt != null && !expiresAt.isAfter(now); }
}
