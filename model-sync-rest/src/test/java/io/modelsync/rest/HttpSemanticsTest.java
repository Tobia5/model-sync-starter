package io.modelsync.rest;

import io.modelsync.core.SyncExceptions;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class HttpSemanticsTest {
    @Test void deltaRetryAfterRemainsRelativeUntilTheDatabaseClockIsAvailable(){
        var failure=assertInstanceOf(SyncExceptions.RateLimitedAfter.class,HttpSemantics.failure(429,"120"));assertEquals(Duration.ofSeconds(120),failure.delay());
    }
    @Test void malformedRetryAfterUsesConservativeFallback(){
        var failure=assertInstanceOf(SyncExceptions.RateLimitedAfter.class,HttpSemantics.failure(429,"bad"));assertEquals(Duration.ofSeconds(60),failure.delay());
    }
    @Test void authFailureIsNotTreatedAsMissingData(){assertInstanceOf(SyncExceptions.Permanent.class,HttpSemantics.failure(403,null));}
}
