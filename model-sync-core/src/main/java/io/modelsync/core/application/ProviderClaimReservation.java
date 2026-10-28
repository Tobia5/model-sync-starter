package io.modelsync.core.application;

import java.time.Instant;

/** Ephemeral scheduling ownership only; never an owner permit or a durable work lease. */
record ProviderClaimReservation(String token, Instant until) {}
