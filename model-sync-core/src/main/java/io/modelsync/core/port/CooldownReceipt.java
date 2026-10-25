package io.modelsync.core.port;

import java.util.Objects;

/** Canonical, first-accepted request and its independently committed publication state. */
public record CooldownReceipt(CooldownRequest request, CooldownState state, boolean durable) {
  public CooldownReceipt {
    Objects.requireNonNull(request); Objects.requireNonNull(state);
    if (state == CooldownState.DURABLE_PENDING && !durable)
      throw new IllegalArgumentException("Pending cooldown requires distributed durability");
  }
}
