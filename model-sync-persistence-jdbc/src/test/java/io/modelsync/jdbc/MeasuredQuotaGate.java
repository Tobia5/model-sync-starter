package io.modelsync.jdbc;

import io.modelsync.core.QuotaAdmission;
import io.modelsync.core.port.QuotaGate;
import java.time.Instant;
import java.util.Optional;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Counts actual JdbcQuotaGate decisions, separately from claims and controlled owner entries. */
final class MeasuredQuotaGate implements QuotaGate {
  private final QuotaGate delegate;
  private final boolean schedulingHints;
  final AtomicLong granted = new AtomicLong();
  final AtomicLong denied = new AtomicLong();
  final AtomicLong hints = new AtomicLong();
  final AtomicLong cooldowns = new AtomicLong();
  final AtomicLong acquisitions = new AtomicLong();
  final AtomicLong failures = new AtomicLong();
  final AtomicLong active = new AtomicLong();
  final AtomicLong published = new AtomicLong();
  final AtomicLong cooldownFailures = new AtomicLong();
  final AtomicLong cooldownActive = new AtomicLong();
  final AtomicLong refusals = new AtomicLong(), knownFailures = new AtomicLong(), unknownFailures = new AtomicLong(), uncertainCommits = new AtomicLong();
  private final ComparisonAccounting accounting;
  private final Map<DetachedFailure, Long> failureFacts = new LinkedHashMap<>();

  MeasuredQuotaGate(QuotaGate delegate) { this(delegate, true); }
  MeasuredQuotaGate(QuotaGate delegate, boolean schedulingHints) { this(delegate, schedulingHints, null); }
  MeasuredQuotaGate(QuotaGate delegate, boolean schedulingHints, ComparisonAccounting accounting) {
    this.delegate = delegate; this.schedulingHints = schedulingHints; this.accounting = accounting;
  }

  @Override public QuotaAdmission acquire(String group) {
    var attempt = accounting == null ? null : accounting.current();
    acquisitions.incrementAndGet(); active.incrementAndGet();
    if (attempt != null) {
      attempt.acquisitions++; attempt.acquisitionActive++;
      if (attempt.claims != 1 || attempt.acquisitions != 1) attempt.violations++;
    }
    try {
      var result = delegate.acquire(group);
      (result.granted() ? granted : denied).incrementAndGet();
      if (attempt != null) { if (result.granted()) attempt.grants++; else attempt.denials++; }
      return result;
    } catch (RuntimeException | Error failure) {
      failures.incrementAndGet(); var fact = DetachedFailure.capture(failure); record(fact);
      switch (fact.category()) {
        case "LOCAL_CONTENTION" -> refusals.incrementAndGet();
        case "KNOWN", "LEASE_LOST" -> knownFailures.incrementAndGet();
        case "UNCERTAIN_COMMIT" -> uncertainCommits.incrementAndGet();
        default -> unknownFailures.incrementAndGet();
      }
      if (attempt != null) { attempt.acquisitionFailures++; attempt.fault("ACQUIRE", fact); }
      throw failure;
    } finally { active.decrementAndGet(); if (attempt != null) attempt.acquisitionActive--; }
  }

  @Override public Optional<Instant> nextAdmission(String group) {
    hints.incrementAndGet();
    return schedulingHints ? delegate.nextAdmission(group) : Optional.empty();
  }

  @Override public void cooldown(String group, Instant until) {
    var attempt = accounting == null ? null : accounting.current();
    cooldowns.incrementAndGet(); cooldownActive.incrementAndGet();
    if (attempt != null) { attempt.cooldownAttempts++; attempt.cooldownActive++; }
    try {
      delegate.cooldown(group, until);
      published.incrementAndGet(); if (attempt != null) attempt.cooldownPublished++;
    } catch (RuntimeException | Error failure) {
      cooldownFailures.incrementAndGet(); var fact = DetachedFailure.capture(failure); record(fact);
      if (attempt != null) { attempt.cooldownFailures++; attempt.fault("COOLDOWN", fact); }
      throw failure;
    } finally { cooldownActive.decrementAndGet(); if (attempt != null) attempt.cooldownActive--; }
  }

  private synchronized void record(DetachedFailure fact) {
    if (failureFacts.size() >= 32 && !failureFacts.containsKey(fact))
      fact = new DetachedFailure("UNKNOWN", "TYPE_LIMIT", null, null, true);
    failureFacts.merge(fact, 1L, Long::sum);
  }

  synchronized List<Map<String, Object>> failureFacts() {
    return failureFacts.entrySet().stream().map(entry -> {
      var fact = new LinkedHashMap<>(entry.getKey().facts()); fact.put("count", entry.getValue()); return (Map<String,Object>)fact;
    }).toList();
  }

  Map<String, Long> counters() {
    var result = new LinkedHashMap<String, Long>();
    result.put("acquisitions", acquisitions.get()); result.put("grants", granted.get()); result.put("denials", denied.get());
    result.put("acquisition_failures", failures.get()); result.put("acquisition_active", active.get());
    result.put("acquisition_refusals", refusals.get()); result.put("acquisition_known_failures", knownFailures.get());
    result.put("acquisition_unknown_failures", unknownFailures.get()); result.put("acquisition_uncertain_commits", uncertainCommits.get());
    result.put("cooldown_attempts", cooldowns.get()); result.put("cooldown_published", published.get());
    result.put("cooldown_failures", cooldownFailures.get()); result.put("cooldown_active", cooldownActive.get());
    return result;
  }
}
