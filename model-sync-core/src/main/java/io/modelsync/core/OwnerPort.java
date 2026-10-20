package io.modelsync.core;

/**
 * Marker interface for authoritative owner ports.
 *
 * <p>An owner can be local or remote. The core deliberately exposes no implicit write-back method
 * because ownership and repair are declared on {@link ModelBinding}.
 *
 * @param <T> synchronized model type
 */
public sealed interface OwnerPort<T> permits BlockingOwner, AsyncOwner {}
