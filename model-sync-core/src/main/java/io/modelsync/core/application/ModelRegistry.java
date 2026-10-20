package io.modelsync.core.application;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;

import java.util.*;

/** Registry of model bindings available to a Model Sync application. */
public final class ModelRegistry {
  private final Map<String, ModelBinding<?>> bindings;

  /**
   * Creates a registry from binding definitions.
   *
   * @param definitions binding definitions to register
   */
  public ModelRegistry(Collection<? extends ModelBinding<?>> definitions) {
    Map<String, ModelBinding<?>> all = new LinkedHashMap<>();
    for (ModelBinding<?> b : definitions)
      if (all.putIfAbsent(b.id(), b) != null)
        throw new IllegalArgumentException("Duplicate binding: " + b.id());
    bindings = Collections.unmodifiableMap(all);
  }

  /**
   * Looks up a binding by identifier.
   *
   * @param id binding identifier
   * @return registered binding
   */
  public ModelBinding<?> get(String id) {
    ModelBinding<?> b = bindings.get(id);
    if (b == null) throw new IllegalArgumentException("Unknown binding: " + id);
    return b;
  }

  /**
   * Looks up a binding by identifier and model type.
   *
   * @param id binding identifier
   * @param type expected model type
   * @return typed binding
   * @param <T> synchronized model type
   */
  public <T> ModelBinding<T> get(String id, Class<T> type) {
    ModelBinding<?> b = get(id);
    if (!b.modelType().equals(type)) throw new IllegalArgumentException("Model type mismatch");
    @SuppressWarnings("unchecked")
    ModelBinding<T> result = (ModelBinding<T>) b;
    return result;
  }

  /**
   * Looks up the only binding registered for a model type.
   *
   * @param type model type
   * @return typed binding
   * @param <T> synchronized model type
   */
  public <T> ModelBinding<T> get(Class<T> type) {
    List<ModelBinding<?>> found =
        bindings.values().stream().filter(b -> b.modelType().equals(type)).toList();
    if (found.size() != 1)
      throw new IllegalArgumentException(
          "Use binding ID: expected one route for " + type.getName() + ", got " + found.size());
    return get(found.getFirst().id(), type);
  }

  /**
   * Returns all registered bindings.
   *
   * @return immutable binding collection
   */
  public Collection<ModelBinding<?>> all() {
    return bindings.values();
  }

  /**
   * Returns bindings that belong to a boundary.
   *
   * @param id boundary identifier
   * @return matching bindings
   */
  public List<ModelBinding<?>> boundary(String id) {
    return bindings.values().stream().filter(b -> b.boundary().equals(id)).toList();
  }
}
