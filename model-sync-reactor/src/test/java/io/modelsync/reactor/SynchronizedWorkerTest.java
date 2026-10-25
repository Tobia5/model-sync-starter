package io.modelsync.reactor;

import static org.junit.jupiter.api.Assertions.*;
import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.AsyncOwner;
import io.modelsync.testkit.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.scheduler.VirtualTimeScheduler;

class SynchronizedWorkerTest {
  @Test void synchronizedFixedPolicyHasNoUniversalEightPerSecondGuarantee() {
    assertTrue(measure(false, 0) < 160, "Fixed250ms and synchronized provider polls cannot promise160 in20s");
  }
  @Test void adaptiveFiveMillisecondOwnerModelRetainsBudgetAndCompletes160() {
    assertEquals(160, measure(true, 5));
  }
  @Test void fixedFiveMillisecondOwnerModelIsMeasuredWithoutAssumingItsCause() {
    int completed = measure(false, 5);
    assertTrue(completed > 0 && completed < 160, "Measured synchronized5ms fixed model does not meet160/20s");
  }
  @Test void knownHintFixedControlRetainsAuthoritativeAdmissionAndReportsItsOwnTiming() { assertTrue(measure(false,5,true)>0); }
  @Test void knownHintAdaptiveControlRetainsAuthoritativeAdmissionAndReportsItsOwnTiming() { assertTrue(measure(true,5,true)>0); }
  private int measure(boolean adaptive, int ownerMillis) {
    return measure(adaptive,ownerMillis,false);
  }
  private int measure(boolean adaptive, int ownerMillis, boolean knownHints) {
    var time = VirtualTimeScheduler.getOrSet();
    try {
      var f = new CoreFixture(); var clock = new SchedulerClock(time); var gate = new MemoryQuotaGate(clock);
      gate.register("owner", Duration.ofMillis(20)); var calls = new AtomicInteger();
      AsyncOwner<String> owner = request -> Mono.delay(Duration.ofMillis(ownerMillis), time)
          .map(ignored -> { calls.incrementAndGet(); return FetchResult.found("value", "v1"); }).toFuture();
      var binding = ModelBinding.builder("model", String.class).boundary("synthetic-synchronized")
          .asyncOwner("owner", owner).replica(f.projection).repairFromOwner().quota("owner")
          .refreshMode(RefreshMode.ON_DEMAND).fingerprint(Digests::sha256Fields).build();
      var registry = new ModelRegistry(List.of(binding));
      var coordinator = new SyncCoordinator(registry, f.store, f.tx, knownHints?new ModelHintQuota(gate,clock):gate, clock, SyncOptions.defaults(), WeightedPriorities.defaults(), () -> .5);
      var gateway = new SyncGateway(registry, f.store, f.tx, clock);
      for (int n = 0; n < 160; n++) gateway.request("model", "synthetic", "k" + n, Priority.URGENT, true);
      var worker = new ReactorSyncWorker(coordinator, Schedulers.immediate(), Schedulers.immediate(), 4,
          Duration.ofMillis(250), Duration.ofMillis(adaptive ? 5 : 250), Duration.ofMillis(adaptive ? 1000 : 250), Duration.ofSeconds(5), clock);
      var subscription = Flux.merge(worker.lane(), worker.lane(), worker.lane(), worker.lane()).subscribe();
      time.advanceTimeBy(Duration.ofSeconds(20)); int completed = (int) worker.activity().fetched();
      int callsAtBoundary=calls.get();long grantsAtBoundary=gate.grants();
      subscription.dispose(); time.advanceTimeBy(Duration.ofMillis(10));
      assertEquals(0, worker.physicalOperations()); assertEquals(0, worker.activity().inFlight());
      assertTrue(worker.activity().maximumInFlight() <= 4); assertEquals(gate.grants(), calls.get());
      assertEquals(completed,callsAtBoundary);assertEquals(grantsAtBoundary,gate.grants(),"No new admission after cancellation");
      assertEquals(completed,worker.activity().fetched(),"Late owner completion cannot be applied after cancellation");
      if(knownHints)assertEquals(0,worker.activity().deferred(),"Hints/reservations prevent racing model admission");
      System.out.printf("SYNCHRONIZED_MODEL known_hints=%s adaptive=%s owner_ms=%d boundary_ms=20000 completions=%d grants=%d ticks=%d late_unapplied=%d%n", knownHints, adaptive, ownerMillis, completed, gate.grants(), worker.activity().ticks(),calls.get()-completed);
      return completed;
    } finally { VirtualTimeScheduler.reset(); }
  }
}
