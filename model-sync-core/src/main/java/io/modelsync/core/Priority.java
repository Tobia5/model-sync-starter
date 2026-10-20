package io.modelsync.core;

/** Work priority used by the coordinator when selecting ready synchronization tasks. */
public enum Priority {
  /** User-facing or operator-forced work that should run first. */
  URGENT,
  /** High-priority application work. */
  HIGH,
  /** Default synchronization priority. */
  NORMAL,
  /** Maintenance or scheduled refresh work. */
  BACKGROUND
}
