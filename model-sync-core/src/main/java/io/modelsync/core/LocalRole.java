package io.modelsync.core;

/**
 * Role of the local projection for one model binding.
 *
 * <p>Authority is attached to the binding's owner route, not to the Java model class itself.
 */
public enum LocalRole {
  /** Local projection is intended to mirror source-owned fields. */
  REPLICA,
  /** Local projection is a bounded cache for serving local reads. */
  CACHE
}
