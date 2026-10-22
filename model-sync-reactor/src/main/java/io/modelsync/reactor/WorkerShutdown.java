package io.modelsync.reactor;

/** Counts at the stop decision; a timed-out result never asserts physical drain. */
public record WorkerShutdown(WorkerShutdownStatus status, long elapsedMillis,
    int logicalInFlight, int physicalOperations, int ownerOperations) {}
