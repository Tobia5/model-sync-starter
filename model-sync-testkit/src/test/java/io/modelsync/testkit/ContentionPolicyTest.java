package io.modelsync.testkit;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.*;
import io.modelsync.core.application.SyncCoordinator;
import io.modelsync.core.port.LocalContention;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ContentionPolicyTest {
  @Test void refusedCooldownIsReportedOnceAndDurableLeaseRemainsRecoverable() {
    var f = new CoreFixture(); var gate = new RefusingCooldownGate(f.gate);
    var coordinator = new SyncCoordinator(f.registry, f.store, f.tx, gate, f.clock,
        f.coordinator.options(), WeightedPriorities.defaults(), () -> 0.5);
    f.enqueue(); var lease = coordinator.claim().orElseThrow();
    coordinator.prepare(lease).orElseThrow(); assertEquals(1, f.gate.grants());
    var rateLimited = new RateLimitedAfter(Duration.ofSeconds(30));
    var failure = assertThrows(LocalContention.class, () -> coordinator.failed(lease, rateLimited));
    assertArrayEquals(new Throwable[] {rateLimited}, failure.getSuppressed());
    assertEquals(1, gate.writes); assertEquals("LEASED", f.store.inspect(f.key).status());
    assertEquals(lease.token(), f.store.inspect(f.key).token()); assertEquals(0, f.store.inspect(f.key).attempts());
    gate.refuse = false; coordinator.failed(lease, rateLimited);
    assertEquals(2, gate.writes); assertEquals("DEFERRED", f.store.inspect(f.key).lastCode());
    assertEquals("WAITING", f.store.inspect(f.key).status());
    assertEquals(f.clock.instant().plusSeconds(30), f.gate.acquire("owner").retryAt());
    assertEquals(1, f.gate.grants()); assertEquals(0, f.calls.get());
  }
  @Test void localContentionHasDistinctCodeBoundedBackoffAndAttemptLimit() {
    var f = new CoreFixture();
    var c = new SyncCoordinator(f.registry, f.store, f.tx, f.gate, f.clock,
        new SyncOptions(Duration.ofSeconds(60), Duration.ofSeconds(15),
            Duration.ofSeconds(2), Duration.ofSeconds(3), 3), WeightedPriorities.defaults(), () -> 0.5);
    f.enqueue();
    for (int n = 1; n <= 3; n++) {
      var lease = c.claim().orElseThrow();
      var now = f.clock.instant();
      c.failed(lease, new LocalContention("test lock", null));
      var row = f.store.inspect(f.key);
      assertEquals("LOCAL_CONTENTION", row.lastCode());
      assertEquals(n, row.attempts());
      assertEquals(now.plusMillis(n == 1 ? 1500 : 2250), row.due());
      assertEquals(n == 3 ? "DEAD" : "WAITING", row.status());
      var passive = f.tx.required(() -> f.store.enqueueRefresh(f.key, Priority.URGENT, now));
      assertEquals(n == 3, passive.isEmpty());
      assertEquals(row.due(), f.store.inspect(f.key).due());
      assertEquals(row.generation(), f.store.inspect(f.key).generation());
      assertTrue(c.claim().isEmpty(), "Never retry immediately");
      f.clock.advance(Duration.between(now, row.due()));
    }
    assertTrue(c.claim().isEmpty());
    assertEquals(0, f.calls.get());
  }

  @Test void rollbackPrecedesRetryAndPerformedOwnerAdmissionIsNotRefunded() throws Exception {
    var f = new CoreFixture(); f.enqueue();
    var lease = f.coordinator.claim().orElseThrow();
    var prepared = f.coordinator.prepare(lease).orElseThrow();
    assertFalse(f.tx.active());
    ((io.modelsync.core.port.BlockingOwner<String>) f.binding.owner()).fetch(prepared.request());
    var until = f.clock.instant().plusSeconds(30); f.gate.cooldown("owner", until);
    var refusal = assertThrows(LocalContention.class, () -> f.tx.required(() -> {
      f.projection.compareAndSet(f.key.subject(), null, "uncommitted", null);
      throw new LocalContention("result application", null);
    }));
    assertTrue(f.projection.load(f.key.subject()).isEmpty());
    f.coordinator.failed(lease, refusal);
    assertEquals("LOCAL_CONTENTION", f.store.inspect(f.key).lastCode());
    var admission = f.gate.acquire("owner");
    assertFalse(admission.granted()); assertEquals(until, admission.retryAt());
    assertEquals(1L, f.gate.grants(), "The already performed owner call keeps its admission");
    assertEquals(1, f.calls.get());
    assertTrue(f.coordinator.claim().isEmpty());
  }

  @Test void failurePersistenceRejectsAnUnfinishedAttemptTransaction() {
    var f = new CoreFixture(); f.enqueue(); var lease = f.coordinator.claim().orElseThrow();
    var original = new LocalContention("unfinished", null);
    var failure = assertThrows(IllegalStateException.class, () -> f.tx.required(() -> {
      f.coordinator.failed(lease, original); return null;
    }));
    assertArrayEquals(new Throwable[] {original}, failure.getSuppressed());
    assertEquals("LEASED", f.store.inspect(f.key).status());
    assertEquals(0, f.store.inspect(f.key).attempts());
  }

  @Test void newerGenerationAndObsoleteLeaseRemainProtected() {
    var f = new CoreFixture(); f.enqueue(); var old = f.coordinator.claim().orElseThrow();
    f.force(); f.coordinator.failed(old, new LocalContention("result", null));
    var pending = f.store.inspect(f.key);
    assertEquals(2, pending.generation()); assertEquals(0, pending.attempts());
    assertEquals(Priority.URGENT, pending.priority());
    f.clock.advance(Duration.ofSeconds(2)); var current = f.coordinator.claim().orElseThrow();
    f.coordinator.failed(old, new LocalContention("obsolete", null));
    assertEquals(current.token(), f.store.inspect(f.key).token());
  }
}
