package io.modelsync.jdbc;

import io.modelsync.core.QuotaAdmission;
import io.modelsync.core.port.QuotaGate;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Counts actual JdbcQuotaGate decisions, separately from claims and controlled owner entries. */
final class MeasuredQuotaGate implements QuotaGate {
  private final QuotaGate delegate;
  private final boolean schedulingHints;
  final AtomicLong granted = new AtomicLong();
  final AtomicLong denied = new AtomicLong();
  final AtomicLong hints = new AtomicLong();
  final AtomicLong cooldowns = new AtomicLong();

  MeasuredQuotaGate(QuotaGate delegate) { this(delegate, true); }
  MeasuredQuotaGate(QuotaGate delegate, boolean schedulingHints) { this.delegate = delegate; this.schedulingHints = schedulingHints; }

  @Override public QuotaAdmission acquire(String group) {
    var result = delegate.acquire(group);
    (result.granted() ? granted : denied).incrementAndGet();
    return result;
  }

  @Override public Optional<Instant> nextAdmission(String group) {
    hints.incrementAndGet();
    return schedulingHints ? delegate.nextAdmission(group) : Optional.empty();
  }

  @Override public void cooldown(String group, Instant until) {
    cooldowns.incrementAndGet();
    delegate.cooldown(group, until);
  }
}
