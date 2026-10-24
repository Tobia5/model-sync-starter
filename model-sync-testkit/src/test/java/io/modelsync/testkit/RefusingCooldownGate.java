package io.modelsync.testkit;

import io.modelsync.core.QuotaAdmission;
import io.modelsync.core.port.*;
import java.time.Instant;

/** Explicit failure seam; acquire still uses the shared authoritative budget. */
final class RefusingCooldownGate implements QuotaGate {
  final MemoryQuotaGate delegate;
  boolean refuse = true;
  int writes;
  RefusingCooldownGate(MemoryQuotaGate delegate) { this.delegate = delegate; }
  public QuotaAdmission acquire(String group) { return delegate.acquire(group); }
  public void cooldown(String group, Instant until) {
    writes++;
    if (refuse) throw new LocalContention("quota cooldown", null);
    delegate.cooldown(group, until);
  }
}
