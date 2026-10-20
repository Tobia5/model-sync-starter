package io.modelsync.testkit;

import io.modelsync.core.QuotaGate;
import java.time.*;
import java.util.*;

/** In-memory quota gate backed by a caller-supplied clock. */
public final class MemoryQuotaGate implements QuotaGate {
  private record Quota(Duration spacing, Instant next) {}

  private final Clock clock;
  private final Map<String, Quota> quotas = new HashMap<>();
  private long grants;

  /**
   * Creates an in-memory quota gate using the supplied clock for all admission and cooldown
   * decisions.
   *
   * <p>The clock is intentionally injected so tests can advance time deterministically without
   * sleeping. The gate stores quota state only in memory and is therefore suitable for tests and
   * local fixtures, not for cross-process production admission control.
   *
   * @param clock clock used to read the current instant
   */
  public MemoryQuotaGate(Clock clock) {
    this.clock = clock;
  }

  /**
   * Registers a quota group with a minimum spacing between granted admissions.
   *
   * <p>Registering the same group repeatedly with the same spacing is idempotent. Registering an
   * existing group with a different spacing fails because changing quota policy mid-test can hide
   * ordering and retry bugs. A newly registered group is immediately eligible for its first grant.
   *
   * @param group quota group identifier used by bindings and owner calls
   * @param spacing positive duration that must elapse between granted admissions for the group
   * @throws IllegalArgumentException when {@code spacing} is zero or negative, or when the group is
   *     already registered with a different spacing
   */
  public synchronized void register(String group, Duration spacing) {
    if (spacing.isNegative() || spacing.isZero())
      throw new IllegalArgumentException("Positive spacing required");
    Quota old = quotas.get(group);
    if (old != null && !old.spacing().equals(spacing))
      throw new IllegalArgumentException("Quota mismatch");
    if (old == null) quotas.put(group, new Quota(spacing, Instant.MIN));
  }

  /**
   * Attempts to acquire admission for one owner call in the given quota group.
   *
   * <p>If the current clock instant is before the group's next allowed instant, the method returns
   * a denied admission with the retry instant and does not increment the grant count. Otherwise it
   * grants admission, advances the group's next allowed instant by the registered spacing, and
   * increments the total grant counter.
   *
   * @param group registered quota group identifier
   * @return admission decision indicating either immediate allowance or the instant at which the
   *     caller should retry
   * @throws NullPointerException when the quota group has not been registered
   */
  @Override
  public synchronized Admission acquire(String group) {
    Quota q = Objects.requireNonNull(quotas.get(group), "Unknown quota");
    Instant now = clock.instant();
    if (now.isBefore(q.next())) return Admission.waitUntil(q.next());
    quotas.put(group, new Quota(q.spacing(), now.plus(q.spacing())));
    grants++;
    return Admission.allow();
  }

  /**
   * Applies a provider cooldown to a quota group.
   *
   * <p>The cooldown is monotonic: the stored next-admission instant is extended only when {@code
   * until} is later than the current next-admission instant. Earlier cooldown values are ignored so
   * one response cannot shorten an already stricter wait.
   *
   * @param group registered quota group identifier
   * @param until earliest instant at which the group may be admitted again
   * @throws NullPointerException when the quota group has not been registered
   */
  @Override
  public synchronized void cooldown(String group, Instant until) {
    Quota q = Objects.requireNonNull(quotas.get(group));
    quotas.put(group, new Quota(q.spacing(), until.isAfter(q.next()) ? until : q.next()));
  }

  /**
   * Returns the number of successful admissions granted by this gate.
   *
   * <p>Denied admissions and cooldown calls do not affect this counter. Tests use this value to
   * assert that quota checks did not accidentally permit extra owner calls.
   *
   * @return cumulative count of granted admissions
   */
  public synchronized long grants() {
    return grants;
  }
}
