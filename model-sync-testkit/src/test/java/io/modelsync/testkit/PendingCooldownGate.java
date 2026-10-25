package io.modelsync.testkit;

import io.modelsync.core.QuotaAdmission;
import io.modelsync.core.port.*;
import java.time.Instant;

/** Pure application-policy fake only; database/process durability is verified separately on Oracle. */
final class PendingCooldownGate implements QuotaGate {
  private final QuotaGate delegate;
  PendingCooldownGate(QuotaGate delegate) { this.delegate=delegate; }
  @Override public QuotaAdmission acquire(String group) { return delegate.acquire(group); }
  @Override public void cooldown(String group,Instant until) { delegate.cooldown(group,until); }
  @Override public CooldownReceipt publishCooldown(CooldownRequest request) {
    delegate.cooldown(request.group(),request.until());
    return new CooldownReceipt(request,CooldownState.DURABLE_PENDING,true);
  }
}
