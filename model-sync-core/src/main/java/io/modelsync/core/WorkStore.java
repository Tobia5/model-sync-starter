package io.modelsync.core;

import java.time.*;
import java.util.Optional;
/** Methods run inside LocalTransactions. Completion follows lockValid in the same transaction. */
public interface WorkStore {
    String enqueue(WorkKey key, Priority priority, boolean force, Instant now);
    Optional<WorkLease> claim(Priority priority, Instant now, Duration leaseTime);
    /** Acquires a DB row lock. A reclaimed token must never pass this check. */
    boolean lockValid(WorkLease lease, Instant now);
    SyncState state(WorkKey key);
    void complete(WorkLease lease, SyncState state, Instant nextAt, Instant now);
    void reschedule(WorkLease lease, Instant nextAt, String errorCode, boolean failure, int maxAttempts, Instant now);
}
