package io.modelsync.testkit;

/** In-memory rollback support for core contract tests, not a production transaction manager. */
public interface SnapshotResource {
  Object snapshot();

  void restore(Object snapshot);
}
