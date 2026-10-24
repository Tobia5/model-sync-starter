package io.modelsync.testkit;

import io.modelsync.core.QuotaAdmission;
import io.modelsync.core.port.QuotaGate;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

/** Deliberately independent hint/admission state, including stale and racing hints. */
record SchedulingQuotaGate(QuotaGate delegate, Function<String, Optional<Instant>> hints) implements QuotaGate {
  @Override public Optional<Instant> nextAdmission(String group) { return hints.apply(group); }
  @Override public QuotaAdmission acquire(String group) { return delegate.acquire(group); }
  @Override public void cooldown(String group, Instant until) { delegate.cooldown(group, until); }
}
