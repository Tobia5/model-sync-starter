package io.modelsync.reactor;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import io.modelsync.testkit.CoreFixture;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class WorkerPacingTest {
  @Test void idleDeferredAndFailureBackOffAndSuccessfulFetchResetsOnlyThisLane() {
    var pacing = new WorkerPacing(Duration.ofMillis(5), Duration.ofMillis(250), Duration.ofSeconds(1));
    assertEquals(Duration.ofMillis(250), pacing.after(WorkerTickResult.EMPTY));
    assertEquals(Duration.ofMillis(500), pacing.after(WorkerTickResult.DEFERRED));
    assertEquals(Duration.ofSeconds(1), pacing.after(WorkerTickResult.FAILED));
    assertEquals(Duration.ofSeconds(1), pacing.after(WorkerTickResult.EMPTY));
    assertEquals(Duration.ofMillis(5), pacing.after(WorkerTickResult.FETCHED));
    assertEquals(Duration.ofMillis(250), pacing.after(WorkerTickResult.EMPTY));
    assertThrows(IllegalArgumentException.class,
        () -> new WorkerPacing(Duration.ZERO, Duration.ofMillis(1), Duration.ofSeconds(1)));
    assertThrows(IllegalArgumentException.class,
        () -> new WorkerPacing(Duration.ofMillis(1), Duration.ofSeconds(1), Duration.ofMillis(1)));
    var enormous = new WorkerPacing(Duration.ofNanos(1), Duration.ofSeconds(Long.MAX_VALUE / 2 + 1),
        Duration.ofSeconds(Long.MAX_VALUE));
    enormous.after(WorkerTickResult.EMPTY);
    assertEquals(Duration.ofSeconds(Long.MAX_VALUE), enormous.after(WorkerTickResult.EMPTY));
  }

  @Test void realLaneRechecksExternalEnqueueAtIdleBoundAndCancelsItsNextPoll() {
    var fixture = new CoreFixture();
    var worker = new ReactorSyncWorker(fixture.coordinator, Schedulers.immediate(), Schedulers.immediate(),
        1, Duration.ofMillis(250), Duration.ofMillis(5), Duration.ofSeconds(1));
    StepVerifier.withVirtualTime(worker::lane)
        .thenAwait(Duration.ofMillis(250)).expectNext(WorkerTickResult.EMPTY)
        .thenAwait(Duration.ofMillis(500)).expectNext(WorkerTickResult.EMPTY)
        .then(fixture::enqueue)
        .thenAwait(Duration.ofSeconds(1)).expectNext(WorkerTickResult.EMPTY)
        .thenAwait(Duration.ofMillis(5)).expectNext(WorkerTickResult.FETCHED)
        .thenCancel().verify(Duration.ofSeconds(3));
    assertEquals(1, fixture.calls.get());
    long ticks = worker.activity().ticks();
    assertEquals(0, worker.activity().inFlight());
    assertEquals(1, worker.activity().maximumInFlight());
    assertEquals(ticks, worker.activity().empty() + worker.activity().fetched());
  }

  @Test void legacyConstructorKeepsFixedDelayIncludingProductiveTicks() {
    var fixture = new CoreFixture();
    fixture.enqueue();
    var worker = new ReactorSyncWorker(fixture.coordinator, Schedulers.immediate(), Schedulers.immediate(),
        1, Duration.ofMillis(250));
    StepVerifier.withVirtualTime(worker::lane)
        .thenAwait(Duration.ofMillis(249)).expectNoEvent(Duration.ofMillis(1))
        .expectNext(WorkerTickResult.FETCHED).thenCancel().verify(Duration.ofSeconds(3));
  }

  @Test void ownerFailureUsesIdleDelayAndDoesNotTurnIntoProductiveBusyPolling() {
    var fixture = new CoreFixture();
    fixture.behavior.set(request -> { throw new io.modelsync.core.Permanent("synthetic"); });
    fixture.enqueue();
    var worker = new ReactorSyncWorker(fixture.coordinator, Schedulers.immediate(), Schedulers.immediate(),
        1, Duration.ofMillis(250), Duration.ofMillis(5), Duration.ofSeconds(1));
    StepVerifier.withVirtualTime(worker::lane)
        .expectSubscription().expectNoEvent(Duration.ofMillis(249)).thenAwait(Duration.ofMillis(1)).expectNext(WorkerTickResult.FAILED)
        .thenAwait(Duration.ofMillis(500)).expectNext(WorkerTickResult.EMPTY)
        .thenCancel().verify(Duration.ofSeconds(3));
    assertEquals(1, fixture.calls.get());
    assertEquals(1, worker.activity().failed());
    assertEquals(0, worker.activity().fetched());
  }
}
