package io.modelsync.testkit;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.port.LocalTransactions;
import java.util.*;
import java.util.function.Supplier;

/** In-memory transaction boundary that snapshots registered resources and rolls back on failure. */
public final class MemoryTransactions implements LocalTransactions {
  private final List<SnapshotResource> resources = new ArrayList<>();
  private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);

  public synchronized void register(SnapshotResource resource) {
    resources.add(resource);
  }

  @Override
  public synchronized <T> T required(Supplier<T> work) {
    if (active()) return work.get();
    List<Object> before = resources.stream().map(SnapshotResource::snapshot).toList();
    active.set(true);
    try {
      return work.get();
    } catch (RuntimeException | Error failure) {
      for (int i = resources.size() - 1; i >= 0; i--) resources.get(i).restore(before.get(i));
      throw failure;
    } finally {
      active.remove();
    }
  }

  @Override
  public boolean active() {
    return active.get();
  }
}
