package io.modelsync.reactor;

/**
 * Instance-local scheduling counters, not provider admission or committed synchronization counts.
 * Deferred includes RetryLater and prepare-only paths (including already-fresh local completion).
 */
public record WorkerActivity(long ticks, long empty, long deferred, long failed, long fetched,
    long cancelled, int inFlight, int maximumInFlight) {}
