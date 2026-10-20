package io.modelsync.core;

import java.time.Instant;
public record WorkLease(WorkKey key, String token, long generation, int attempts,
                        boolean force, Priority priority, Instant expiresAt) { }
