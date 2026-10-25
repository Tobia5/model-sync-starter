package io.modelsync.jdbc;

import java.util.*;

/** One run-local numeric correlation; deliberately contains no WorkKey or lease token. */
final class ComparisonAttempt {
  final long id;
  final String phase;
  int claims, acquisitions, grants, denials, acquisitionFailures, acquisitionActive;
  int ownerCalls, ownerActive, admissionUses, completions, reschedules, fenceRejections, localConflicts;
  int cooldownAttempts, cooldownPublished, cooldownFailures, cooldownActive, violations;
  Boolean returned;
  final Map<String, DetachedFailure> faults = new LinkedHashMap<>();

  ComparisonAttempt(long id, String phase) { this.id = id; this.phase = phase; }

  void fault(String site, Throwable failure) { fault(site, DetachedFailure.capture(failure)); }
  void fault(String site, DetachedFailure failure) {
    if (faults.putIfAbsent(site, failure) != null) violations++;
  }

  String terminal() {
    if (faults.containsKey("FAILURE_METADATA") || faults.containsKey("COOLDOWN")
        || faults.containsKey("ESCAPED")) return "FAILURE_HANDLING_FAILED";
    if (claims == 0) return Boolean.FALSE.equals(returned) && acquisitions == 0 && ownerCalls == 0
        && faults.isEmpty() ? "EMPTY" : "UNRESOLVED";
    if (claims != 1 || !Boolean.TRUE.equals(returned) || acquisitions > 1) return "UNRESOLVED";
    if (completions == 1 && reschedules == 0) {
      if (acquisitions == 0 && ownerCalls == 0 && faults.isEmpty()) return "FRESH_NO_FETCH";
      if (grants == 1 && ownerCalls == 1 && faults.isEmpty()) return "RECONCILED";
    }
    if (fenceRejections > 0 && completions == 0 && reschedules == 0) return "FENCED";
    if (reschedules != 1 || completions != 0) return "UNRESOLVED";
    if (acquisitions == 0 && ownerCalls == 0 && faults.containsKey("PREPARE")) return "PRE_ADMISSION_FAILED";
    if (acquisitionFailures == 1 && ownerCalls == 0 && faults.containsKey("ACQUIRE")) return "ACQUISITION_FAILED";
    if (denials == 1 && ownerCalls == 0 && faults.isEmpty()) return "DENIED";
    if (grants == 1 && ownerCalls == 1 && faults.containsKey("OWNER")) return "OWNER_FAILED";
    if (grants == 1 && ownerCalls == 1 && faults.containsKey("RESULT")) return "RESULT_FAILED";
    if (grants == 1 && ownerCalls == 1 && localConflicts == 1 && faults.isEmpty()) return "RESULT_DEFERRED";
    return "UNRESOLVED";
  }

  Map<String, Object> snapshot() {
    var v = new LinkedHashMap<String, Object>();
    v.put("id", id); v.put("phase", phase); v.put("claims", claims); v.put("returned", returned);
    v.put("acquisitions", acquisitions); v.put("grants", grants); v.put("denials", denials);
    v.put("acquisition_failures", acquisitionFailures); v.put("acquisition_active", acquisitionActive);
    v.put("owner_calls", ownerCalls); v.put("owner_active", ownerActive); v.put("admission_uses", admissionUses);
    v.put("completions", completions); v.put("reschedules", reschedules); v.put("fence_rejections", fenceRejections);
    v.put("local_conflicts", localConflicts);
    v.put("cooldown_attempts", cooldownAttempts); v.put("cooldown_published", cooldownPublished);
    v.put("cooldown_failures", cooldownFailures); v.put("cooldown_active", cooldownActive);
    v.put("violations", violations); v.put("terminal", terminal());
    var detached = new LinkedHashMap<String, Object>(); faults.forEach((site, fact) -> detached.put(site, fact.facts()));
    v.put("faults", detached); return v;
  }
}
