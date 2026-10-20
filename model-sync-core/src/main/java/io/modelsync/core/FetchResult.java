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
public record FetchResult<T>(FetchStatus status, T value, String validator, Instant sourceChangedAt) {
  public FetchResult {
    Objects.requireNonNull(status);
    if (validator != null
        && (validator.length() > 1024
            || validator.indexOf('\r') >= 0
            || validator.indexOf('\n') >= 0))
      throw new Permanent("INVALID_VALIDATOR");
    if (status == FetchStatus.FOUND && value == null)
      throw new IllegalArgumentException("FOUND needs a value");
    if (status != FetchStatus.FOUND && value != null)
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
    return new FetchResult<>(FetchStatus.FOUND, v, etag, null);
  }

  /**
   * Creates a not-modified result.
   *
   * @param etag optional refreshed owner validator
   * @return unchanged result
   * @param <T> synchronized model type
   */
  public static <T> FetchResult<T> unchanged(String etag) {
    return new FetchResult<>(FetchStatus.NOT_MODIFIED, null, etag, null);
  }

  /**
   * Creates an absent result.
   *
   * @return absent result
   * @param <T> synchronized model type
   */
  public static <T> FetchResult<T> absent() {
    return new FetchResult<>(FetchStatus.ABSENT, null, null, null);
  }
}
