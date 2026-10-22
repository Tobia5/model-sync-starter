package io.modelsync.reactor;

/** Bounded lifecycle result, distinct from subscription cancellation. */
public enum WorkerShutdownStatus { NOT_STOPPED, DRAINED, TIMED_OUT }
