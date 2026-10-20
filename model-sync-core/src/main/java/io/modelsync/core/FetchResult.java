package io.modelsync.core;

import java.time.Instant;
import java.util.Objects;

/**
 * Result returned by an authoritative owner fetch.
 *
 * @param <T> synchronized model type
 * @param status owner response status
 * @param value model value when {@code status == FOUND}
 * @param validator owner validator such as an ETag, if supplied
 * @param sourceChangedAt owner-supplied source-change timestamp, if supplied
 */
public record FetchResult<T>(Status status, T value, String validator, Instant sourceChangedAt) {
  /** Owner response category understood by the coordinator. */
  public enum Status {
    /** Owner returned the current value. */
    FOUND,
    /** Owner reported that the caller's validator is still current. */
    NOT_MODIFIED,
    /** Owner reported that the subject is absent. */
    ABSENT
  }

  public FetchResult {
    Objects.requireNonNull(status);
    if (validator != null
        && (validator.length() > 1024
            || validator.indexOf('\r') >= 0
            || validator.indexOf('\n') >= 0))
      throw new SyncExceptions.Permanent("INVALID_VALIDATOR");
    if (status == Status.FOUND && value == null)
      throw new IllegalArgumentException("FOUND needs a value");
    if (status != Status.FOUND && value != null)
      throw new IllegalArgumentException("Unexpected value");
  }

  /**
   * Creates a found result without an owner-supplied changed timestamp.
   *
   * @param v owner value
   * @param etag optional owner validator
   * @return found result
   * @param <T> synchronized model type
   */
  public static <T> FetchResult<T> found(T v, String etag) {
    return new FetchResult<>(Status.FOUND, v, etag, null);
  }

  /**
   * Creates a not-modified result.
   *
   * @param etag optional refreshed owner validator
   * @return unchanged result
   * @param <T> synchronized model type
   */
  public static <T> FetchResult<T> unchanged(String etag) {
    return new FetchResult<>(Status.NOT_MODIFIED, null, etag, null);
  }

  /**
   * Creates an absent result.
   *
   * @return absent result
   * @param <T> synchronized model type
   */
  public static <T> FetchResult<T> absent() {
    return new FetchResult<>(Status.ABSENT, null, null, null);
  }
}
