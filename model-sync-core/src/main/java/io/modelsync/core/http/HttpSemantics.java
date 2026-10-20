package io.modelsync.core.http;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.RateLimitedAfter;
import io.modelsync.core.RateLimited;
import io.modelsync.core.Permanent;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** HTTP status and retry-header translation utilities for owner adapters. */
public final class HttpSemantics {
  private HttpSemantics() {}

  public static Instant retryAfter(String value, Instant now) {
    if (value != null) {
      try {
        long seconds = Long.parseLong(value.trim());
        if (seconds >= 0) return now.plusSeconds(seconds);
      } catch (RuntimeException ignored) {
        /* HTTP-date is the other supported form. */
      }
      try {
        Instant t = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        return t.isAfter(now) ? t : now.plusMillis(1);
      } catch (RuntimeException ignored) {
        /* Malformed provider hint: conservative fallback. */
      }
    }
    return now.plusSeconds(60);
  }

  public static RuntimeException failure(int code, String retryAfter) {
    if (code == 429 || (code == 503 && retryAfter != null)) {
      if (retryAfter != null) {
        try {
          long seconds = Long.parseLong(retryAfter.trim());
          if (seconds >= 0 && seconds <= 31_536_000L)
            return new RateLimitedAfter(Duration.ofSeconds(seconds));
        } catch (NumberFormatException ignored) {
        }
        try {
          return new RateLimited(
              ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
        } catch (RuntimeException ignored) {
        }
      }
      return new RateLimitedAfter(Duration.ofSeconds(60));
    }
    if (code >= 500 || code == 408) return new IllegalStateException("HTTP_TEMPORARY");
    return new Permanent("HTTP_" + code);
  }
}
