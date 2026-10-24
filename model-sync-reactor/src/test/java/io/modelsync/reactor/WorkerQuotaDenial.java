package io.modelsync.reactor;

import io.modelsync.core.QuotaAdmission;
import io.modelsync.core.port.QuotaGate;
import java.time.Instant;

/** Unknown advisory hint with an authoritative denial, as in a cross-worker admission race. */
final class WorkerQuotaDenial implements QuotaGate {
  public QuotaAdmission acquire(String group) {
    return new QuotaAdmission(false, Instant.parse("2027-01-01T00:00:00Z"));
  }
  public void cooldown(String group, Instant until) {}
}
