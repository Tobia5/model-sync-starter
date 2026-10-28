package io.modelsync.core;

import java.time.Instant;

public record QuotaAdmission(boolean granted, Instant retryAt) {
  /**
   * Creates an immediate admission result.
   *
   * @return granted admission
   */
  public static QuotaAdmission allow() {
    return new QuotaAdmission(true, null);
  }

  /**
   * Creates a deferred admission result.
   *
   * @param t retry instant
   * @return denied admission with retry time
   */
  public static QuotaAdmission waitUntil(Instant t) {
    return new QuotaAdmission(false, t);
  }
}
