package io.modelsync.rest;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.SyncExceptions;
import java.time.*;
import org.junit.jupiter.api.Test;

class HttpSemanticsTest {
  @Test
  void deltaRetryAfterRemainsRelativeUntilTheDatabaseClockIsAvailable() {
    var failure =
        assertInstanceOf(SyncExceptions.RateLimitedAfter.class, HttpSemantics.failure(429, "120"));
    assertEquals(Duration.ofSeconds(120), failure.delay());
  }

  @Test
  void malformedRetryAfterUsesConservativeFallback() {
    var failure =
        assertInstanceOf(SyncExceptions.RateLimitedAfter.class, HttpSemantics.failure(429, "bad"));
    assertEquals(Duration.ofSeconds(60), failure.delay());
  }

  @Test
  void authFailureIsNotTreatedAsMissingData() {
    assertInstanceOf(SyncExceptions.Permanent.class, HttpSemantics.failure(403, null));
  }
}
