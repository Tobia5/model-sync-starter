package io.modelsync.jdbc;

import java.util.*;

/** Strict raw-event reconciliation, independently checked again by the Python evidence gate. */
final class ComparisonAccountingValidator {
  private ComparisonAccountingValidator() {}
  private static void require(boolean value, String message) {
    if (!value) throw new IllegalStateException("Comparison accounting: " + message);
  }
  private static long number(Map<?, ?> value, String key) {
    Object v = value.get(key);
    require(v instanceof Long || v instanceof Integer, "missing/nonintegral " + key);
    long n = ((Number)v).longValue(); require(n >= 0, "negative " + key); return n;
  }

  static void check(Map<String, Object> value, long physicalCalls, long committedSyncs) {
    require(number(value,"schema") == 1 && number(value,"active_attempts") == 0, "undrained attempts");
    require(number(value,"stop_admission_ns") >= number(value,"started_ns")
        && number(value,"drained_ns") >= number(value,"stop_admission_ns"), "invalid phase boundary");
    var boundary = (Map<?, ?>) value.get("quota_boundary");
    var attempts = (List<?>) value.get("attempts");
    require(attempts != null && attempts.size() <= number(value,"correlation_limit"), "missing/boundless correlations");
    var ids = new HashSet<Long>(); var totals = new HashMap<String, Long>();
    long claimed = 0, returns = 0, reconciled = 0;
    for (Object item : attempts) {
      var a = (Map<?, ?>) item;
      require(ids.add(number(a,"id")) && Objects.equals(a.get("phase"), value.get("phase")), "duplicate/cross-phase attempt");
      for (String key : List.of("claims","acquisitions","grants","denials","acquisition_failures",
          "owner_calls","admission_uses","completions","reschedules","cooldown_attempts","cooldown_published","cooldown_pending","cooldown_durable","cooldown_failures"))
        totals.merge(key, number(a,key), Long::sum);
      require(number(a,"violations") == 0 && number(a,"acquisition_active") == 0
          && number(a,"owner_active") == 0 && number(a,"cooldown_active") == 0, "active operation/invalid admission use");
      long c=number(a,"claims"), q=number(a,"acquisitions"), g=number(a,"grants"), d=number(a,"denials"), f=number(a,"acquisition_failures");
      require(c <= 1 && q <= c && q == g+d+f, "acquisition cardinality/partition");
      require(number(a,"owner_calls") == g && number(a,"admission_uses") == g, "owner missing/duplicating committed admission");
      require(number(a,"cooldown_attempts") == number(a,"cooldown_published")+number(a,"cooldown_pending")+number(a,"cooldown_failures"), "cooldown publication partition");
      require(number(a,"cooldown_durable") <= number(a,"cooldown_attempts") && number(a,"cooldown_pending") <= number(a,"cooldown_durable"), "invalid durable receipt count");
      require(number(a,"cooldown_failures") == 0, "failed cooldown publication");
      claimed += c; if (Boolean.TRUE.equals(a.get("returned"))) returns++;
      var faults = (Map<?, ?>)a.get("faults"); require(faults != null, "missing detached failure facts");
      for (var entry : faults.entrySet()) {
        var fact=(Map<?, ?>)entry.getValue();
        require(!Boolean.TRUE.equals(fact.get("truncated")) && Set.of("LOCAL_CONTENTION","KNOWN","LEASE_LOST").contains(fact.get("category")), "unknown/uncertain failure");
        if (entry.getKey().equals("ACQUIRE")) require(fact.get("category").equals("LOCAL_CONTENTION")
            && fact.get("vendor_code") instanceof Number code && code.intValue()==54, "unconfirmed/unexpected acquisition failure");
      }
      require(!faults.containsKey("FAILURE_METADATA") && !faults.containsKey("ESCAPED"), "failure metadata did not persist");
      String terminal = Objects.toString(a.get("terminal"));
      require(Set.of("EMPTY","FRESH_NO_FETCH","FENCED","PRE_ADMISSION_FAILED","ACQUISITION_FAILED","DENIED","RECONCILED","OWNER_FAILED","RESULT_FAILED","RESULT_DEFERRED").contains(terminal), "unresolved claimed attempt");
      require(c == 1 ? Boolean.TRUE.equals(a.get("returned")) && !terminal.equals("EMPTY")
          : Boolean.FALSE.equals(a.get("returned")) && terminal.equals("EMPTY"), "claim/return mismatch");
      require(q == 0 ? Set.of("EMPTY","FRESH_NO_FETCH","PRE_ADMISSION_FAILED","FENCED").contains(terminal)
          : !Set.of("EMPTY","FRESH_NO_FETCH","PRE_ADMISSION_FAILED").contains(terminal), "missing acquisition event");
      require((f == 1) == faults.containsKey("ACQUIRE"), "missing/double acquisition failure event");
      long complete=number(a,"completions"), retry=number(a,"reschedules");
      boolean exact = switch (terminal) {
        case "EMPTY" -> c==0 && q==0 && complete==0 && retry==0 && faults.isEmpty();
        case "FRESH_NO_FETCH" -> c==1 && q==0 && complete==1 && retry==0 && faults.isEmpty();
        case "FENCED" -> c==1 && number(a,"fence_rejections")>0 && complete==0 && retry==0;
        case "PRE_ADMISSION_FAILED" -> q==0 && complete==0 && retry==1 && faults.keySet().equals(Set.of("PREPARE"));
        case "ACQUISITION_FAILED" -> f==1 && complete==0 && retry==1 && faults.keySet().equals(Set.of("ACQUIRE"));
        case "DENIED" -> d==1 && complete==0 && retry==1 && faults.isEmpty();
        case "RECONCILED" -> g==1 && complete==1 && retry==0 && faults.isEmpty();
        case "OWNER_FAILED" -> g==1 && complete==0 && retry==1 && faults.keySet().equals(Set.of("OWNER"));
        case "RESULT_FAILED" -> g==1 && complete==0 && retry==1 && faults.keySet().equals(Set.of("RESULT"));
        case "RESULT_DEFERRED" -> g==1 && complete==0 && retry==1 && number(a,"local_conflicts")==1 && faults.isEmpty();
        default -> false;
      };
      require(exact, "missing/contradictory attribution event");
      if (terminal.equals("RECONCILED")) reconciled++;
    }
    require(claimed == returns, "committed claim/returned result mismatch");
    for (String key : List.of("acquisitions","grants","denials","acquisition_failures","cooldown_attempts","cooldown_published","cooldown_pending","cooldown_durable","cooldown_failures"))
      require(totals.getOrDefault(key,0L) == number(boundary,key), "boundary/detail mismatch: "+key);
    require(number(boundary,"acquisition_active") == 0 && number(boundary,"cooldown_active") == 0, "physical quota boundary active");
    require(number(boundary,"acquisition_failures") == number(boundary,"acquisition_refusals")
        + number(boundary,"acquisition_known_failures") + number(boundary,"acquisition_unknown_failures")
        + number(boundary,"acquisition_uncertain_commits"), "failure classification partition");
    require(number(boundary,"acquisition_refusals") == number(boundary,"acquisition_failures")
        && number(boundary,"acquisition_unknown_failures") == 0 && number(boundary,"acquisition_uncertain_commits") == 0,
        "non-contention acquisition failures cannot qualify");
    var recorded = (Map<?, ?>)value.get("totals");
    for (var entry : totals.entrySet()) require(entry.getValue() == number(recorded,entry.getKey()), "summary/detail mismatch: "+entry.getKey());
    require(totals.getOrDefault("owner_calls",0L) == physicalCalls, "independent owner-call count mismatch");
    require(reconciled == number(recorded,"committed_reconciliations") && reconciled == committedSyncs, "fresh committed-state count mismatch");
    require(claimed == number(boundary,"acquisitions") + number(recorded,"no_acquisition_claims"), "claimed attempt partition");
  }
}
