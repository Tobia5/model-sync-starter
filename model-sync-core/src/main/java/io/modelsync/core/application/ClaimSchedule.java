package io.modelsync.core.application;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** One poll's immutable hint snapshot and locally reserved provider lanes. */
record ClaimSchedule(Optional<Set<String>> waitingBindings,
    Map<String, ProviderClaimReservation> reservations) {}
