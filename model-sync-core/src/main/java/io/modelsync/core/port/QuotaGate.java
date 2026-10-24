package io.modelsync.core.port;

import io.modelsync.core.*;
import io.modelsync.core.domain.*;

import java.time.Instant;
import java.util.Optional;

/** QuotaAdmission-control port for shared owner quotas and provider cooldowns. */
public interface QuotaGate {
  /**
   * QuotaAdmission decision for one owner quota group.
   *
   * @param granted whether the owner call may proceed immediately
   * @param retryAt next instant to retry when not granted
   */

  /**
   * Attempts to acquire quota for one owner call.
   *
   * @param group quota group identifier
   * @return admission decision
   */
  QuotaAdmission acquire(String group);

  /**
   * Non-consuming scheduling hint, read outside the work transaction. Empty means unknown.
   * A past/absent hint NEVER grants admission; acquire remains authoritative before owner I/O.
   * A stale hint can delay work or lose an admission race, but cannot spend a permit.
   */
  default Optional<Instant> nextAdmission(String group) { return Optional.empty(); }

  /**
   * Applies or extends a provider cooldown.
   *
   * @param group quota group identifier
   * @param until cooldown end instant
   */
  void cooldown(String group, Instant until);
}
