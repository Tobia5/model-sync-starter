package io.modelsync.core;

/** An owner can be local or remote; there is deliberately no implicit write-back method. */
public sealed interface OwnerPort<T> permits BlockingOwner, AsyncOwner { }
