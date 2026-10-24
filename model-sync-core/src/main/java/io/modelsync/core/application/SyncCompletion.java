package io.modelsync.core.application;
import io.modelsync.core.port.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.DoubleSupplier;

/** One responsibility of the synchronization application service. */
final class SyncCompletion {
  private final WorkStore store;
  SyncCompletion(WorkStore store) { this.store = store; }
  void complete(ModelBinding<?> binding, WorkLease lease, SyncState state,
                        Instant nextAt, Instant now) {
    if (binding.refreshMode() == RefreshMode.ON_DEMAND) {
      store.completeOnDemand(lease, state, now);
    } else {
      store.complete(lease, state, nextAt, now);
    }
  }
}
