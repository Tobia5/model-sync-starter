package io.modelsync.core;

import java.time.Clock;
import java.util.List;

/** Entry point for requesting model synchronization and creating typed model facades. */
public final class SyncGateway {
  private final ModelRegistry registry;
  private final WorkStore store;
  private final LocalTransactions tx;
  private final Clock clock;

  /**
   * Creates a gateway.
   *
   * @param registry model registry
   * @param store durable work store
   * @param tx local transaction boundary
   * @param clock synchronization clock
   */
  public SyncGateway(ModelRegistry registry, WorkStore store, LocalTransactions tx, Clock clock) {
    this.registry = registry;
    this.store = store;
    this.tx = tx;
    this.clock = clock;
  }

  /**
   * Looks up a binding by identifier.
   *
   * @param id binding identifier
   * @return registered binding
   */
  public ModelBinding<?> binding(String id) {
    return registry.get(id);
  }

  /**
   * Creates a typed facade for the only binding of the given model type.
   *
   * @param type model type
   * @return typed synchronization facade
   * @param <T> synchronized model type
   */
  public <T> ModelSync<T> model(Class<T> type) {
    return new ModelSync<>(registry.get(type), store, tx, clock);
  }

  /**
   * Creates a typed facade for a specific binding.
   *
   * @param binding binding identifier
   * @param type model type
   * @return typed synchronization facade
   * @param <T> synchronized model type
   */
  public <T> ModelSync<T> model(String binding, Class<T> type) {
    return new ModelSync<>(registry.get(binding, type), store, tx, clock);
  }

  /**
   * Requests synchronization without first creating a typed facade.
   *
   * @param binding binding identifier
   * @param tenant tenant identifier
   * @param id subject identifier
   * @param p requested priority
   * @param force whether to bypass normal freshness checks
   * @return durable work ticket identifier
   */
  public String request(String binding, String tenant, String id, Priority p, boolean force) {
    registry.get(binding);
    return tx.required(
        () ->
            store.enqueue(
                new WorkKey(binding, new SubjectKey(tenant, id)), p, force, clock.instant()));
  }

  /**
   * Returns registered model bindings for a boundary.
   *
   * <p>This returns model types configured in this application, not every row at the external
   * owner.
   *
   * @param boundary boundary identifier
   * @return registered bindings for the boundary
   */
  public List<ModelBinding<?>> boundaryModels(String boundary) {
    return registry.boundary(boundary);
  }
}
