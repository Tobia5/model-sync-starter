package io.modelsync.testkit;

import io.modelsync.core.*;
import java.time.Instant;
import java.util.*;

/** In-memory projection store for tests and offline contract fixtures. */
public final class MemoryProjectionStore<T> implements ProjectionStore<T>, SnapshotResource {
  private Map<SubjectKey, Projection<T>> rows = new HashMap<>();
  public boolean rejectNextWrite;

  @Override
  public synchronized Optional<Projection<T>> load(SubjectKey key) {
    return Optional.ofNullable(rows.get(key));
  }

  @Override
  public synchronized boolean compareAndSet(
      SubjectKey key, String expected, T value, Instant expiresAt) {
    if (rejectNextWrite) {
      rejectNextWrite = false;
      return false;
    }
    Projection<T> old = rows.get(key);
    if (!Objects.equals(old == null ? null : old.revision(), expected)) return false;
    String revision = Long.toString(old == null ? 1 : Long.parseLong(old.revision()) + 1);
    rows.put(key, new Projection<>(value, revision, expiresAt));
    return true;
  }

  public synchronized void evict(SubjectKey key) {
    rows.remove(key);
  }

  @Override
  public synchronized Object snapshot() {
    return new HashMap<>(rows);
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized void restore(Object before) {
    rows = new HashMap<>((Map<SubjectKey, Projection<T>>) before);
  }
}
