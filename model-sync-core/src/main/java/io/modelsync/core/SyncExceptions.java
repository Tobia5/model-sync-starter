package io.modelsync.core;

import java.time.Instant;

/** Exception types used to communicate coordinator control-flow outcomes. */
public final class SyncExceptions {
  private SyncExceptions() {}

  /** Raised when a claimed work lease is no longer current. */
  public static final class LeaseLost extends RuntimeException {
    /** Creates a lease-lost exception. */
    public LeaseLost() {
      super("Lease is no longer current");
    }
  }

  /** Base class for failures that should be retried no earlier than a known instant. */
  public static class RetryLater extends RuntimeException {
    private final Instant retryAt;

    /**
     * Creates a retry-later exception.
     *
     * @param retryAt earliest retry instant
     */
    public RetryLater(Instant retryAt) {
      super("Deferred by admission policy");
      this.retryAt = java.util.Objects.requireNonNull(retryAt);
    }

    /**
     * Returns the earliest retry instant.
     *
     * @return retry instant
     */
    public Instant retryAt() {
      return retryAt;
    }
  }

  /** Raised by owner adapters when the provider returns a relative cooldown. */
  public static final class RateLimitedAfter extends RuntimeException {
    private final java.time.Duration delay;

    /**
     * Creates a relative rate-limit exception.
     *
     * @param delay cooldown duration
     */
    public RateLimitedAfter(java.time.Duration delay) {
      super("Provider cooldown");
      this.delay = java.util.Objects.requireNonNull(delay);
      if (delay.isNegative()) throw new IllegalArgumentException("Negative cooldown");
    }

    /**
     * Returns the cooldown duration.
     *
     * @return cooldown duration
     */
    public java.time.Duration delay() {
      return delay;
    }
  }

  /** Raised when the provider supplies an absolute retry instant. */
  public static final class RateLimited extends RetryLater {
    /**
     * Creates an absolute rate-limit exception.
     *
     * @param t earliest retry instant
     */
    public RateLimited(Instant t) {
      super(t);
    }
  }

  /** Raised for permanent adapter or protocol failures. */
  public static final class Permanent extends RuntimeException {
    private final String code;

    /**
     * Creates a permanent exception with a stable error code.
     *
     * @param code stable error code
     */
    public Permanent(String code) {
      super(code);
      this.code = SubjectKey.checked(code, "errorCode", 64);
    }

    /**
     * Returns the stable error code.
     *
     * @return error code
     */
    public String code() {
      return code;
    }
  }
}
