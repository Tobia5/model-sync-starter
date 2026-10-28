package io.modelsync.testkit;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import java.time.*;
import java.util.*;

/** In-memory durable work store used by the contract test fixture. */
public final class MemoryWorkStore implements WorkStore, SnapshotResource {
  /** In-memory row representation for claimed and waiting work. */

  private Map<String, MemoryWorkCell> rows = new LinkedHashMap<>();
  private Map<String, SyncState> states = new HashMap<>();
  public boolean failNextComplete;

  @Override
  public synchronized String enqueue(WorkKey key, Priority p, boolean force, Instant now) {
    Objects.requireNonNull(p);
    String id = key.storageId();
    MemoryWorkCell old = rows.get(id);
    if (old == null) rows.put(id, new MemoryWorkCell(key, p, 1, 0, force, "WAITING", null, null, now, null));
    else {
      Priority best = old.priority().ordinal() < p.ordinal() ? old.priority() : p;
      rows.put(
          id,
          new MemoryWorkCell(
              key,
              best,
              old.generation() + 1,
              old.status().equals("DEAD") ? 0 : old.attempts(),
              old.force() || force,
              old.status().equals("LEASED") ? "LEASED" : "WAITING",
              old.token(),
              old.until(),
              now.isBefore(old.due()) ? now : old.due(),
              old.lastCode()));
    }
    return id;
  }

  @Override
  public synchronized Optional<String> enqueueRefresh(WorkKey key, Priority priority, Instant now) {
    Objects.requireNonNull(priority, "priority");
    MemoryWorkCell c = rows.get(key.storageId());
    if (c == null) return Optional.of(enqueue(key, priority, false, now));
    if (!c.key().equals(key)) throw new IllegalStateException("Work identity collision");
    if (c.status().equals("DEAD")) return Optional.empty();
    if (c.status().equals("LEASED")) return Optional.of(key.storageId());
    boolean idle = c.status().equals("IDLE");
    Instant due = idle ? now : c.lastCode() == null && now.isBefore(c.due()) ? now : c.due();
    Priority best = c.priority().ordinal() < priority.ordinal() ? c.priority() : priority;
    rows.put(key.storageId(), new MemoryWorkCell(key, best, idle ? c.generation() + 1 : c.generation(),
        c.attempts(), c.force(), "WAITING", c.token(), c.until(), due, c.lastCode()));
    return Optional.of(key.storageId());
  }

  @Override
  public synchronized Optional<WorkLease> claim(Priority p, Instant now, Duration duration) {
    return claim(p, now, duration, null);
  }

  @Override
  public synchronized Optional<WorkLease> claim(Priority p, Instant now, Duration duration, Set<String> waitingBindings) {
    Optional<MemoryWorkCell> selected =
        rows.values().stream()
            .filter(c -> c.priority() == p)
            .filter(
                c ->
                    (c.status().equals("WAITING") && !c.due().isAfter(now)
                            && (waitingBindings == null || waitingBindings.contains(c.key().binding())))
                        || (c.status().equals("LEASED") && !c.until().isAfter(now)))
            .min(Comparator.comparing(MemoryWorkCell::due).thenComparing(c -> c.key().storageId()));
    if (selected.isEmpty()) return Optional.empty();
    MemoryWorkCell c = selected.get();
    String token = UUID.randomUUID().toString();
    Instant until = now.plus(duration);
    rows.put(
        c.key().storageId(),
        new MemoryWorkCell(
            c.key(),
            c.priority(),
            c.generation(),
            c.attempts(),
            c.force(),
            "LEASED",
            token,
            until,
            c.due(),
            c.lastCode()));
    return Optional.of(
        new WorkLease(c.key(), token, c.generation(), c.attempts(), c.force(), p, until));
  }

  @Override
  public synchronized boolean lockValid(WorkLease lease, Instant now) {
    MemoryWorkCell c = rows.get(lease.key().storageId());
    return c != null
        && c.status().equals("LEASED")
        && Objects.equals(c.token(), lease.token())
        && c.until().isAfter(now);
  }

  @Override
  public synchronized SyncState state(WorkKey key) {
    return states.getOrDefault(key.storageId(), SyncState.empty(key));
  }

  private MemoryWorkCell current(WorkLease lease) {
    MemoryWorkCell c = rows.get(lease.key().storageId());
    if (c == null || !c.status().equals("LEASED") || !Objects.equals(c.token(), lease.token()))
      throw new LeaseLost();
    return c;
  }

  @Override
  public boolean supportsOnDemand() {
    return true;
  }

  @Override
  public synchronized void completeOnDemand(WorkLease lease, SyncState state, Instant now) {
    completeInternal(lease, state, null, now);
  }

  @Override
  public synchronized void complete(WorkLease lease, SyncState state, Instant nextAt, Instant now) {
    completeInternal(lease, state, Objects.requireNonNull(nextAt, "nextAt"), now);
  }

  private void completeInternal(WorkLease lease, SyncState state, Instant nextAt, Instant now) {
    if (failNextComplete) {
      failNextComplete = false;
      throw new IllegalStateException("INJECTED_COMPLETION_FAILURE");
    }
    MemoryWorkCell c = current(lease);
    boolean pending = c.generation() != lease.generation();
    states.put(lease.key().storageId(), state);
    rows.put(
        lease.key().storageId(),
        new MemoryWorkCell(
            c.key(),
            pending ? c.priority() : Priority.BACKGROUND,
            c.generation(),
            0,
            pending && c.force(),
            !pending && nextAt == null ? "IDLE" : "WAITING",
            null,
            null,
            pending || nextAt == null ? now : nextAt,
            null));
  }

  @Override
  public synchronized void reschedule(
      WorkLease lease, Instant nextAt, String code, boolean failure, int maxAttempts, Instant now) {
    MemoryWorkCell c = current(lease);
    int attempts = c.generation() != lease.generation() ? 0 : c.attempts() + (failure ? 1 : 0);
    rows.put(
        lease.key().storageId(),
        new MemoryWorkCell(
            c.key(),
            c.priority(),
            c.generation(),
            attempts,
            c.force(),
            attempts >= maxAttempts ? "DEAD" : "WAITING",
            null,
            null,
            nextAt,
            code));
  }

  public synchronized MemoryWorkCell inspect(WorkKey key) {
    return rows.get(key.storageId());
  }

  public synchronized int size() {
    return rows.size();
  }

  @Override
  public synchronized Object snapshot() {
    return new MemoryWorkSnapshot(new LinkedHashMap<>(rows), new HashMap<>(states));
  }

  @Override
  public synchronized void restore(Object before) {
    MemoryWorkSnapshot s = (MemoryWorkSnapshot) before;
    rows = new LinkedHashMap<>(s.rows());
    states = new HashMap<>(s.states());
  }
}
