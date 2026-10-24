package io.modelsync.core.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public enum FreshnessAge {
  /** No trusted validation time or matching digest exists. */
  UNKNOWN,
  /** Projection is younger than {@link FreshnessPolicy#refreshAfter()}. */
  FRESH,
  /**
   * Projection is older than refresh target but still inside {@link FreshnessPolicy#maxAge()}.
   */
  STALE,
  /** Projection reached or exceeded the serving limit. */
  EXPIRED
}
