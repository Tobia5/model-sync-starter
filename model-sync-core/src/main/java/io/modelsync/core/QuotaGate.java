package io.modelsync.core;

import java.time.Instant;

/** Admission-control port for shared owner quotas and provider cooldowns. */
public interface QuotaGate {
  /**
   * Admission decision for one owner quota group.
   *
   * @param granted whether the owner call may proceed immediately
   * @param retryAt next instant to retry when not granted
   */
  record Admission(boolean granted, Instant retryAt) {
    /**
     * Creates an immediate admission result.
     *
     * @return granted admission
     */
    public static Admission allow() {
      return new Admission(true, null);
    }

    /**
     * Creates a deferred admission result.
     *
     * @param t retry instant
     * @return denied admission with retry time
     */
    public static Admission waitUntil(Instant t) {
      return new Admission(false, t);
    }
  }

  /**
   * Attempts to acquire quota for one owner call.
   *
   * @param group quota group identifier
   * @return admission decision
   */
  Admission acquire(String group);

  /**
   * Applies or extends a provider cooldown.
   *
   * @param group quota group identifier
   * @param until cooldown end instant
   */
  void cooldown(String group, Instant until);
}
