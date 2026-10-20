package io.modelsync.core;

import java.time.Instant;
import java.util.Optional;
public interface ProjectionStore<T> {
    Optional<Projection<T>> load(SubjectKey key);
    /** Must CAS atomically in the caller's local transaction. Null revision means insert-if-absent.
     * No external calls; false means a concurrent modification. */
    boolean compareAndSet(SubjectKey key, String expectedRevision, T value, Instant expiresAt);
}
