package io.modelsync.jdbc;

import io.modelsync.core.QuotaAdmission;
import io.modelsync.core.port.QuotaGate;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/** Counts actual JdbcQuotaGate decisions, separately from claims and controlled owner entries. */
final class MeasuredQuotaGate implements QuotaGate {
  private final QuotaGate delegate;
  final AtomicLong granted = new AtomicLong();
  final AtomicLong denied = new AtomicLong();
  final AtomicLong cooldowns = new AtomicLong();

  MeasuredQuotaGate(QuotaGate delegate) { this.delegate = delegate; }

  @Override public QuotaAdmission acquire(String group) {
    var result = delegate.acquire(group);
    (result.granted() ? granted : denied).incrementAndGet();
    return result;
  }

  @Override public void cooldown(String group, Instant until) {
    cooldowns.incrementAndGet();
    delegate.cooldown(group, until);
  }
}
