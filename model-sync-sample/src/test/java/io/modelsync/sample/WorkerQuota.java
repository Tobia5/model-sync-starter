package io.modelsync.sample;

import io.modelsync.core.QuotaAdmission;
import io.modelsync.core.port.*;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

final class WorkerQuota implements QuotaGate {
  private final QuotaGate delegate;
  final AtomicLong grants = new AtomicLong(), denials = new AtomicLong();
  WorkerQuota(QuotaGate delegate) { this.delegate = delegate; }
  public QuotaAdmission acquire(String group) {
    var result = delegate.acquire(group);
    (result.granted() ? grants : denials).incrementAndGet();
    return result;
  }
  public Optional<Instant> nextAdmission(String group) { return delegate.nextAdmission(group); }
  public boolean supportsDurableCooldowns() { return delegate.supportsDurableCooldowns(); }
  public CooldownReceipt publishCooldown(CooldownRequest request) { return delegate.publishCooldown(request); }
  public void cooldown(String group, Instant until) { delegate.cooldown(group, until); }
}
