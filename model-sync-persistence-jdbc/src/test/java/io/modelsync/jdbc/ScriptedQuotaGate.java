package io.modelsync.jdbc;

import io.modelsync.core.QuotaAdmission;
import io.modelsync.core.port.QuotaGate;
import java.time.Instant;
import java.util.function.*;

/** Deterministic test boundary; never a production quota implementation. */
final class ScriptedQuotaGate implements QuotaGate {
  Supplier<QuotaAdmission> admission = QuotaAdmission::allow;
  Consumer<Instant> publication = until -> {};
  @Override public QuotaAdmission acquire(String group) { return admission.get(); }
  @Override public void cooldown(String group, Instant until) { publication.accept(until); }
}
