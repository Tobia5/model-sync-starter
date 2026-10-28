package io.modelsync.reactor;

import io.modelsync.core.QuotaAdmission;
import io.modelsync.core.port.QuotaGate;
import io.modelsync.testkit.MemoryQuotaGate;
import java.time.*;
import java.util.Optional;

/** Deterministic known-hint control. Actual permits still come only from the delegated gate. */
final class ModelHintQuota implements QuotaGate {
  private final MemoryQuotaGate delegate;
  private final Clock clock;
  private Instant hint = Instant.EPOCH;
  ModelHintQuota(MemoryQuotaGate delegate, Clock clock) { this.delegate=delegate;this.clock=clock; }
  public synchronized QuotaAdmission acquire(String group) {
    var result=delegate.acquire(group);
    Instant next=result.granted()?clock.instant().plusMillis(20):result.retryAt();
    if(next.isAfter(hint))hint=next;
    return result;
  }
  public synchronized Optional<Instant> nextAdmission(String group) { return Optional.of(hint); }
  public synchronized void cooldown(String group,Instant until) { delegate.cooldown(group,until);if(until.isAfter(hint))hint=until; }
}
