package io.modelsync.core.port;

import java.time.Instant;

/** Local fail-closed state; does NOT assert that another instance knows the provider response. */
public final class CooldownUnavailable extends RuntimeException {
  private final Instant retryAt;
  public CooldownUnavailable(Instant retryAt) {
    super("Shared cooldown acceptance is unconfirmed"); this.retryAt = retryAt;
  }
  public Instant retryAt() { return retryAt; }
}
