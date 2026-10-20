package io.modelsync.core.port;

import io.modelsync.core.*;
import io.modelsync.core.domain.*;

import java.time.Instant;

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
   * Applies or extends a provider cooldown.
   *
   * @param group quota group identifier
   * @param until cooldown end instant
   */
  void cooldown(String group, Instant until);
}
