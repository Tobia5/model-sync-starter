package io.modelsync.reactor;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;
import io.modelsync.testkit.CoreFixture;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;

class AsyncShutdownTest {
  @Test void timeoutDoesNotCancelTheOwnersFutureOrRecyclePhysicalCapacity() throws Exception {
    var f = new CoreFixture(); var completion = new CompletableFuture<FetchResult<String>>();
    var calls = new AtomicInteger();
    var binding = ModelBinding.builder("customer", String.class).boundary("test")
        .asyncOwner("synthetic", request -> { calls.incrementAndGet(); return completion; })
        .replica(f.projection).repairFromOwner().fingerprint(Digests::sha256Fields).quota("owner")
        .freshness(new FreshnessPolicy(Duration.ofSeconds(10), Duration.ofSeconds(20), Duration.ofSeconds(30))).build();
    var registry = new ModelRegistry(List.of(binding));
    var coordinator = new SyncCoordinator(registry, f.store, f.tx, f.gate, f.clock,
        new SyncOptions(Duration.ofSeconds(5), Duration.ofMillis(50), Duration.ofSeconds(1), Duration.ofSeconds(2), 8),
        WeightedPriorities.defaults(), () -> .5);
    f.enqueue();
    var persistence = Schedulers.newBoundedElastic(1, 8, "async-drain-jdbc");
    var owner = Schedulers.newBoundedElastic(1, 8, "async-drain-owner");
    var worker = new ReactorSyncWorker(coordinator, persistence, owner, 1, Duration.ofMillis(10),
        Duration.ofMillis(5), Duration.ofMillis(40), Duration.ofMillis(100), f.clock);
    try {
      worker.start();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (worker.activity().failed() == 0 && System.nanoTime() < deadline) Thread.sleep(5);
      assertTrue(worker.activity().failed() > 0); assertFalse(completion.isCancelled());
      f.clock.advance(Duration.ofMillis(2)); // Provider spacing must not mask the physical-capacity check.
      f.gateway.request("customer", "tenant-a", "another", Priority.URGENT, true);
      deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (worker.activity().deferred() == 0 && System.nanoTime() < deadline) Thread.sleep(5);
      assertTrue(worker.activity().deferred() > 0); assertEquals(1, calls.get());
      var pending = ((io.modelsync.testkit.MemoryWorkSnapshot) f.store.snapshot()).rows().get(
          new WorkKey("customer", new SubjectKey("tenant-a", "another")).storageId());
      assertTrue(pending.due().isBefore(f.clock.instant().plusSeconds(1)), "capacity retry must use the coordinator clock");
      worker.stop(); assertEquals(WorkerShutdownStatus.TIMED_OUT, worker.shutdown().status());
      assertEquals(1, worker.shutdown().ownerOperations()); assertFalse(completion.isDone());
      completion.complete(FetchResult.found("late", null));
      worker.stop(); assertEquals(WorkerShutdownStatus.DRAINED, worker.shutdown().status());
      assertTrue(f.projection.load(f.key.subject()).isEmpty());
    } finally { completion.complete(FetchResult.found("cleanup", null)); worker.stop(); persistence.dispose(); owner.dispose(); }
  }
}
