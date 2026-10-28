package io.modelsync.core.application;
import io.modelsync.core.port.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;

import java.util.Objects;
import java.util.function.Function;

public final class ModelBindingBuilder<T> {
  final String id;
  final Class<T> type;
  String boundary, ownerSystem, quota;
  OwnerPort<T> owner;
  ProjectionStore<T> projection;
  Function<T, String> digest;
  LocalRole role = LocalRole.REPLICA;
  FreshnessPolicy freshness = FreshnessPolicy.defaults();
  boolean repair;
  RefreshMode refreshMode = RefreshMode.PERIODIC;

  ModelBindingBuilder(String id, Class<T> type) {
    this.id = id;
    this.type = type;
  }

  /**
   * Sets the logical owner boundary.
   *
   * @param value boundary identifier
   * @return this builder
   */
  public ModelBindingBuilder<T> boundary(String value) {
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
  public ModelBindingBuilder<T> owner(String system, BlockingOwner<T> port) {
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
  public ModelBindingBuilder<T> asyncOwner(String system, AsyncOwner<T> port) {
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
  public ModelBindingBuilder<T> replica(ProjectionStore<T> store) {
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
  public ModelBindingBuilder<T> cache(ProjectionStore<T> store) {
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
  public ModelBindingBuilder<T> fingerprint(Function<T, String> function) {
    digest = function;
    return this;
  }

  /**
   * Sets the quota group for owner calls.
   *
   * @param value quota group identifier
   * @return this builder
   */
  public ModelBindingBuilder<T> quota(String value) {
    quota = value;
    return this;
  }

  /**
   * Sets the freshness policy.
   *
   * @param value freshness policy
   * @return this builder
   */
  public ModelBindingBuilder<T> freshness(FreshnessPolicy value) {
    freshness = value;
    return this;
  }

  /**
   * Sets recurrence independently of trigger and worker execution style.
   * ON_DEMAND requires a WorkStore supporting durable idle completion.
   */
  public ModelBindingBuilder<T> refreshMode(RefreshMode value) {
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
  public ModelBindingBuilder<T> repairFromOwner() {
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
