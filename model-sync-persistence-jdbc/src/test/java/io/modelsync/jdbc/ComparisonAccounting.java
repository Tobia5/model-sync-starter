package io.modelsync.jdbc;

import io.modelsync.core.application.SyncCoordinator;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Bounded per-fixture correlation around the unchanged production runOne entry point. */
final class ComparisonAccounting {
  private final int limit;
  private final String id = UUID.randomUUID().toString();
  private final ThreadLocal<ComparisonAttempt> current = new ThreadLocal<>();
  private final List<ComparisonAttempt> attempts = new ArrayList<>();
  private Map<String, Long> before;
  private String phase;
  private long sequence, started, stop, drained;
  private int active, completedDuringDrain;

  ComparisonAccounting() { this(65_536); }
  ComparisonAccounting(int limit) { this.limit = limit; }

  synchronized void begin(String name, MeasuredQuotaGate gate) {
    if (active != 0 || phase != null) throw new IllegalStateException("Previous accounting phase is not drained");
    before = gate.counters();
    if (before.get("acquisition_active") != 0 || before.get("cooldown_active") != 0)
      throw new IllegalStateException("Quota boundary still active");
    attempts.clear(); phase = name; started = System.nanoTime(); stop = 0; drained = 0; completedDuringDrain = 0;
  }

  ComparisonAttempt current() {
    var value = current.get();
    if (value == null) throw new IllegalStateException("Fixture operation has no attempt correlation");
    return value;
  }

  private synchronized boolean enter(BooleanSupplier admit) {
    if (phase == null || current.get() != null) throw new IllegalStateException("Invalid accounting phase/thread");
    if (stop != 0) return false;
    if (!admit.getAsBoolean()) { stop = System.nanoTime(); return false; }
    if (attempts.size() >= limit) throw new IllegalStateException("Run-local correlation bound exhausted");
    var value = new ComparisonAttempt(++sequence, phase); attempts.add(value); current.set(value); active++;
    return true;
  }

  Optional<Boolean> runIf(SyncCoordinator coordinator, BooleanSupplier admit) {
    if (!enter(admit)) return Optional.empty();
    try {
      boolean result = coordinator.runOne(); current().returned = result; return Optional.of(result);
    } catch (RuntimeException | Error failure) {
      current().fault("ESCAPED", failure); throw failure;
    } finally {
      synchronized (this) {
        if (stop != 0) completedDuringDrain++;
        active--; if (active == 0 && stop != 0) drained = System.nanoTime();
      }
      current.remove();
    }
  }

  boolean runOne(SyncCoordinator coordinator) { return runIf(coordinator, () -> true).orElseThrow(); }

  synchronized void stopAdmissions() { if (stop == 0) stop = System.nanoTime(); }

  synchronized Map<String, Object> finish(MeasuredQuotaGate gate) {
    stopAdmissions();
    if (active == 0 && drained == 0) drained = System.nanoTime();
    var after = gate.counters(); var delta = new LinkedHashMap<String, Long>();
    after.forEach((key, value) -> delta.put(key, key.endsWith("active") ? value : value - before.get(key)));
    var result = new LinkedHashMap<String, Object>();
    result.put("schema", 1); result.put("accounting_id", id); result.put("phase", phase);
    result.put("correlation_limit", limit); result.put("active_attempts", active);
    result.put("started_ns", started); result.put("stop_admission_ns", stop); result.put("drained_ns", drained);
    result.put("drain_ms", drained == 0 ? null : (drained - stop) / 1_000_000);
    result.put("completed_during_drain", completedDuringDrain);
    result.put("quota_boundary_before", before); result.put("quota_boundary_after", after); result.put("quota_boundary", delta);
    var snapshots = attempts.stream().map(ComparisonAttempt::snapshot).toList();
    result.put("attempts", snapshots);
    var totals = new LinkedHashMap<String, Long>();
    for (String key : List.of("claims", "acquisitions", "grants", "denials", "acquisition_failures", "owner_calls",
        "admission_uses", "completions", "reschedules", "cooldown_attempts", "cooldown_published", "cooldown_pending", "cooldown_durable", "cooldown_failures"))
      totals.put(key, snapshots.stream().mapToLong(v -> ((Number)v.get(key)).longValue()).sum());
    result.put("totals", totals);
    var terminal = new TreeMap<String, Long>(); attempts.forEach(a -> terminal.merge(a.terminal(), 1L, Long::sum));
    result.put("terminal_counts", terminal);
    totals.put("committed_reconciliations", terminal.getOrDefault("RECONCILED", 0L));
    totals.put("no_acquisition_claims", snapshots.stream().filter(v -> ((Number)v.get("claims")).intValue()==1
        && ((Number)v.get("acquisitions")).intValue()==0).count());
    if (active == 0) phase = null;
    return result;
  }
}
