package io.modelsync.core;

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

  private ModelBinding(Builder<T> b) {
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
  public static <T> Builder<T> builder(String id, Class<T> type) {
    return new Builder<>(id, type);
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
   * Builder for {@link ModelBinding}.
   *
   * @param <T> synchronized model type
   */
  public static final class Builder<T> {
    private final String id;
    private final Class<T> type;
    private String boundary, ownerSystem, quota;
    private OwnerPort<T> owner;
    private ProjectionStore<T> projection;
    private Function<T, String> digest;
    private LocalRole role = LocalRole.REPLICA;
    private FreshnessPolicy freshness = FreshnessPolicy.defaults();
    private boolean repair;
    private RefreshMode refreshMode = RefreshMode.PERIODIC;

    private Builder(String id, Class<T> type) {
      this.id = id;
      this.type = type;
    }

    /**
     * Sets the logical owner boundary.
     *
     * @param value boundary identifier
     * @return this builder
     */
    public Builder<T> boundary(String value) {
      boundary = value;
      return this;
    }

    /**
     * Sets a blocking owner adapter.
     *
     * @param system owner system identifier
     * @param port blocking owner port
     * @return this builder
     */
    public Builder<T> owner(String system, BlockingOwner<T> port) {
      ownerSystem = system;
      owner = port;
      return this;
    }

    /**
     * Sets an asynchronous owner adapter.
     *
     * @param system owner system identifier
     * @param port asynchronous owner port
     * @return this builder
     */
    public Builder<T> asyncOwner(String system, AsyncOwner<T> port) {
      ownerSystem = system;
      owner = port;
      return this;
    }

    /**
     * Configures the projection as a replica.
     *
     * @param store projection store
     * @return this builder
     */
    public Builder<T> replica(ProjectionStore<T> store) {
      role = LocalRole.REPLICA;
      projection = store;
      return this;
    }

    /**
     * Configures the projection as a cache.
     *
     * @param store projection store
     * @return this builder
     */
    public Builder<T> cache(ProjectionStore<T> store) {
      role = LocalRole.CACHE;
      projection = store;
      return this;
    }

    /**
     * Sets the semantic fingerprint function.
     *
     * @param function deterministic digest function for source-owned fields
     * @return this builder
     */
    public Builder<T> fingerprint(Function<T, String> function) {
      digest = function;
      return this;
    }

    /**
     * Sets the quota group for owner calls.
     *
     * @param value quota group identifier
     * @return this builder
     */
    public Builder<T> quota(String value) {
      quota = value;
      return this;
    }

    /**
     * Sets the freshness policy.
     *
     * @param value freshness policy
     * @return this builder
     */
    public Builder<T> freshness(FreshnessPolicy value) {
      freshness = value;
      return this;
    }

    /**
     * Sets recurrence independently of trigger and worker execution style.
     * ON_DEMAND requires a WorkStore supporting durable idle completion.
     */
    public Builder<T> refreshMode(RefreshMode value) {
      refreshMode = Objects.requireNonNull(value, "refreshMode");
      return this;
    }

    /**
     * Enables local repair from owner values.
     *
     * <p>Use this only for projections whose mapped fields are all owned by the configured owner.
     *
     * @return this builder
     */
    public Builder<T> repairFromOwner() {
      repair = true;
      return this;
    }

    /**
     * Builds the binding and validates all required fields.
     *
     * @return immutable model binding
     */
    public ModelBinding<T> build() {
      return new ModelBinding<>(this);
    }
  }
}
