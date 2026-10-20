package io.modelsync.core;

import java.time.Instant;

public final class LeaseLost extends RuntimeException {
  /** Creates a lease-lost exception. */
  public LeaseLost() {
    super("Lease is no longer current");
  }
}
