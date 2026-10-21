package io.modelsync.reactor;

/** Instance-local scheduling counters, not provider admission or committed synchronization counts. */
public record WorkerActivity(long ticks, long empty, long deferred, long failed, long fetched,
    long cancelled, int inFlight, int maximumInFlight) {}
