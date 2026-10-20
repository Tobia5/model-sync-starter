package io.modelsync.rest;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.RateLimitedAfter;
import io.modelsync.core.Permanent;
import io.modelsync.core.http.HttpSemantics;
import java.time.*;
import org.junit.jupiter.api.Test;

class HttpSemanticsTest {
  @Test
  void deltaRetryAfterRemainsRelativeUntilTheDatabaseClockIsAvailable() {
    var failure =
        assertInstanceOf(RateLimitedAfter.class, HttpSemantics.failure(429, "120"));
    assertEquals(Duration.ofSeconds(120), failure.delay());
  }

  @Test
  void malformedRetryAfterUsesConservativeFallback() {
    var failure =
        assertInstanceOf(RateLimitedAfter.class, HttpSemantics.failure(429, "bad"));
    assertEquals(Duration.ofSeconds(60), failure.delay());
  }

  @Test
  void authFailureIsNotTreatedAsMissingData() {
    assertInstanceOf(Permanent.class, HttpSemantics.failure(403, null));
  }
}
