package io.modelsync.core.application;
import io.modelsync.core.port.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;

import java.util.Objects;
import java.util.function.Function;

/**
 * Explicit synchronization route definition.
 *
 * <p>Reusing a DTO class does not imply shared authority, ownership, quota, freshness, or tenant
 * scope. Each binding declares those properties directly.
 *
 * @param <T> synchronized model type
 */
public final class ModelBinding<T> {
  private final String id, boundary, ownerSystem, quota;
  private final Class<T> modelType;
  private final OwnerPort<T> owner;
  private final ProjectionStore<T> projection;
  private final Function<T, String> digest;
  private final LocalRole role;
  private final FreshnessPolicy freshness;
  private final boolean repair;
  private final RefreshMode refreshMode;

  ModelBinding(ModelBindingBuilder<T> b) {
    id = SubjectKey.checked(b.id, "binding", 128);
    boundary = SubjectKey.checked(b.boundary, "boundary", 128);
    ownerSystem = SubjectKey.checked(b.ownerSystem, "ownerSystem", 128);
    quota = SubjectKey.checked(b.quota, "quota", 128);
    modelType = Objects.requireNonNull(b.type);
    owner = Objects.requireNonNull(b.owner, "owner");
    projection = Objects.requireNonNull(b.projection, "projection");
    digest = Objects.requireNonNull(b.digest, "digest");
    role = Objects.requireNonNull(b.role);
    freshness = Objects.requireNonNull(b.freshness);
    repair = b.repair;
    refreshMode = Objects.requireNonNull(b.refreshMode, "refreshMode");
  }

  /**
   * Starts a binding builder.
   *
   * @param id stable binding identifier
   * @param type synchronized model type
   * @return builder for the binding
   * @param <T> synchronized model type
   */
  public static <T> ModelBindingBuilder<T> builder(String id, Class<T> type) {
    return new ModelBindingBuilder<>(id, type);
  }

  /** Returns the stable binding identifier. */
  public String id() {
    return id;
  }

  /** Returns the logical owner boundary. */
  public String boundary() {
    return boundary;
  }

  /** Returns the external owner system identifier. */
  public String ownerSystem() {
    return ownerSystem;
  }

  /** Returns the quota group used for owner admission. */
  public String quota() {
    return quota;
  }

  /** Returns the synchronized model type. */
  public Class<T> modelType() {
    return modelType;
  }

  /** Returns the configured owner adapter. */
  public OwnerPort<T> owner() {
    return owner;
  }

  /** Returns the local projection store. */
  public ProjectionStore<T> projection() {
    return projection;
  }

  /** Returns the local projection role. */
  public LocalRole role() {
    return role;
  }

  /** Returns the freshness policy for this binding. */
  public FreshnessPolicy freshness() {
    return freshness;
  }

  /** Returns whether successful work recurs automatically or waits for another trigger. */
  public RefreshMode refreshMode() {
    return refreshMode;
  }

  /** Returns whether owner values may repair the local projection. */
  public boolean repair() {
    return repair;
  }

  /**
   * Computes and validates the semantic digest for a model value.
   *
   * @param value model value
   * @return validated digest string
   */
  public String digest(T value) {
    return SubjectKey.checked(digest.apply(value), "digest", 256);
  }

  /**
   * ModelBindingBuilder for {@link ModelBinding}.
   *
   * @param <T> synchronized model type
   */

}
