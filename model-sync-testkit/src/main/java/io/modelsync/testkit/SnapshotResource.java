package io.modelsync.testkit;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

/** In-memory rollback support for core contract tests, not a production transaction manager. */
public interface SnapshotResource {
  Object snapshot();

  void restore(Object snapshot);
}
