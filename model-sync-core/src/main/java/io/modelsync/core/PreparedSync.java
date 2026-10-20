package io.modelsync.core;

/**
 * Immutable execution snapshot used by the coordinator.
 *
 * <p>A second local revision check happens before applying owner results, so this snapshot is not
 * sufficient authority to overwrite concurrent local changes.
 *
 * @param lease claimed work lease
 * @param binding binding being synchronized
 * @param local local projection snapshot, or {@code null} when absent
 * @param previous previous synchronization state
 * @param request owner read request
 * @param <T> synchronized model type
 */
public record PreparedSync<T>(
    WorkLease lease,
    ModelBinding<T> binding,
    Projection<T> local,
    SyncState previous,
    ReadRequest request) {}
