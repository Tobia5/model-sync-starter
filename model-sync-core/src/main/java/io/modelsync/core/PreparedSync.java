package io.modelsync.core;

/** Execution data is a snapshot. A second local revision check happens before applying results. */
public record PreparedSync<T>(WorkLease lease, ModelBinding<T> binding, Projection<T> local,
                              SyncState previous, ReadRequest request) { }
