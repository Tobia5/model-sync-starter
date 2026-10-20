package io.modelsync.sample;

/** Only fields owned by the external system belong in this synchronization view. */
public record Customer(String displayName, String status) {}
