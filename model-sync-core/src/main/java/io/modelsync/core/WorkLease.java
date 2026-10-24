package io.modelsync.core;
import io.modelsync.core.domain.*;

import java.time.Instant;

/**
 * Lease returned when a worker claims a unit of synchronization work.
 *
 * @param key claimed work key
 * @param token opaque lease token
 * @param generation work generation claimed by the lease
 * @param attempts failed attempts observed at claim time
 * @param force whether the claimed work should bypass normal freshness checks
 * @param priority priority bucket from which the work was claimed
 * @param until lease expiration instant
 */
public record WorkLease(
    WorkKey key,
    String token,
    long generation,
    int attempts,
    boolean force,
    Priority priority,
    Instant expiresAt) {}
