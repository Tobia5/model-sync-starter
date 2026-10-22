package io.modelsync.reactor;

/** Per-subscription cancellation fence; accessed under the worker's operations monitor. */
final class WorkerExecution { boolean closed; }
