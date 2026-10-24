package io.modelsync.reactor;

/** Scheduling outcome, separate from the public tick's claimed-item boolean. */
enum WorkerTickResult {
  EMPTY,
  DEFERRED,
  FAILED,
  FETCHED
}
