package io.modelsync.testkit;

import static org.junit.jupiter.api.Assertions.*;
import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.QuotaGate;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AdmissionHintsTest {
  private SyncCoordinator coordinator(CoreFixture f, ModelRegistry registry, QuotaGate gate) {
    return new SyncCoordinator(registry, f.store, f.tx, gate, f.clock,
        SyncOptions.defaults(), WeightedPriorities.defaults(), () -> .5);
  }

  @Test void knownFutureHintLeavesWaitingGenerationAndAttemptsUntouched() {
    var f = new CoreFixture(); f.force();
    var before = f.store.inspect(f.key);
    var c = coordinator(f, f.registry, new SchedulingQuotaGate(f.gate,
        group -> Optional.of(f.clock.instant().plusSeconds(30))));
    assertFalse(c.runOne());
    assertEquals(before, f.store.inspect(f.key));
    assertEquals(0, f.gate.grants());
    assertEquals(0, f.calls.get());
  }

  @Test void hintsAreReadOncePerProviderOutsideTheLocalTransaction() {
    var f = new CoreFixture();
    var second = ModelBinding.builder("second", String.class).boundary("hint-check").owner("second", r -> FetchResult.found("v", null))
        .replica(f.projection).fingerprint(Digests::sha256Fields).quota("owner").build();
    var registry = new ModelRegistry(List.of(f.binding, second));
    var reads = new AtomicInteger();
    var c = coordinator(f, registry, new SchedulingQuotaGate(f.gate, group -> {
      assertFalse(f.tx.active()); reads.incrementAndGet(); return Optional.empty();
    }));
    assertFalse(c.runOne());
    assertEquals(1, reads.get());
    assertEquals(0, f.gate.grants(), "Empty polls must not spend quota");
  }

  @Test void expiredHintNeverAuthorizesAnOwnerCall() {
    var f = new CoreFixture(); f.force();
    f.gate.cooldown("owner", f.clock.instant().plusSeconds(30));
    var c = coordinator(f, f.registry, new SchedulingQuotaGate(f.gate,
        group -> Optional.of(f.clock.instant())));
    assertTrue(c.runOne(), "Stale hint may admit a ledger attempt, not an owner call");
    assertEquals(0, f.calls.get());
    assertEquals(0, f.gate.grants());
    assertEquals("WAITING", f.store.inspect(f.key).status());
    assertEquals(0, f.store.inspect(f.key).attempts());
    assertFalse(f.store.inspect(f.key).due().isBefore(f.clock.instant().plusSeconds(30)));
  }

  @Test void cooldownRacingAfterHintStillWinsAuthoritativeAdmission() {
    var f = new CoreFixture(); f.force();
    var c = coordinator(f, f.registry, new SchedulingQuotaGate(f.gate, group -> {
      f.gate.cooldown(group, f.clock.instant().plusSeconds(60)); return Optional.empty();
    }));
    assertTrue(c.runOne());
    assertEquals(0, f.calls.get());
    assertEquals(0, f.gate.grants());
  }

  @Test void futureHintCannotSuppressExpiredLeaseRecovery() {
    var f = new CoreFixture(); f.force();
    var old = f.coordinator.claim().orElseThrow();
    f.clock.advance(SyncOptions.defaults().lease().plusSeconds(1));
    Instant until = f.clock.instant().plusSeconds(30);
    f.gate.cooldown("owner", until);
    var c = coordinator(f, f.registry, new SchedulingQuotaGate(f.gate, group -> Optional.of(until)));
    var recovered = c.claim().orElseThrow();
    assertNotEquals(old.token(), recovered.token());
    assertFalse(f.store.lockValid(old, f.clock.instant()));
    assertTrue(f.store.lockValid(recovered, f.clock.instant()));
    assertThrows(RetryLater.class, () -> c.prepare(recovered));
    assertEquals(0, f.calls.get());
  }

  @Test void anotherProviderCanProgressWhileFirstIsDelayed() {
    var f = new CoreFixture(); f.force();
    f.gate.register("other-quota", Duration.ofMillis(1));
    var calls = new AtomicInteger();
    var other = ModelBinding.builder("other", String.class).boundary("hint-check")
        .owner("other-owner", r -> { calls.incrementAndGet(); return FetchResult.found("other", null); })
        .replica(f.projection).repairFromOwner().fingerprint(Digests::sha256Fields).quota("other-quota").build();
    var registry = new ModelRegistry(List.of(f.binding, other));
    var gateway = new SyncGateway(registry, f.store, f.tx, f.clock);
    gateway.request("other", "tenant-a", "other-key", Priority.BACKGROUND, true);
    var c = coordinator(f, registry, new SchedulingQuotaGate(f.gate,
        group -> group.equals("owner") ? Optional.of(f.clock.instant().plusSeconds(30)) : Optional.empty()));
    assertTrue(c.runOne());
    assertEquals(1, calls.get());
    assertEquals(0, f.calls.get());
    assertEquals("WAITING", f.store.inspect(f.key).status());
  }

  @Test void expiredDeadlineResumesWaitingWithoutChangingItsGeneration() {
    var f = new CoreFixture(); f.force();
    Instant until = f.clock.instant().plusSeconds(30);
    f.gate.cooldown("owner", until);
    var c = coordinator(f, f.registry, new SchedulingQuotaGate(f.gate, group -> Optional.of(until)));
    assertTrue(c.claim().isEmpty());
    f.clock.advance(Duration.ofSeconds(30));
    assertTrue(c.runOne());
    assertEquals(1, f.calls.get());
    assertEquals(1, f.store.inspect(f.key).generation());
  }

  @Test void freshLocalReadsAndVisitsDoNotInspectQuotaOrResetAge() {
    var f = new CoreFixture(); f.force(); f.run();
    var state = f.state();
    f.gate.cooldown("owner", f.clock.instant().plusSeconds(30));
    var model = f.gateway.model("customer", String.class);
    assertTrue(model.freshLocal("tenant-a", "42").isPresent());
    var triggers = new SyncTriggers(f.gateway);
    var visit = triggers.model("customer", String.class).visit("tenant-a", "42", VisitPolicy.REQUIRE_FRESH);
    assertTrue(visit.projection().isPresent());
    assertEquals(state, f.state());
    assertEquals(1, f.calls.get());
    assertEquals(1, f.gate.grants());
  }

  @Test void providerSlotCoversClaimAndPreparationWithoutSpendingQuota() {
    var f = new CoreFixture(); f.force();
    f.gateway.request("customer", "tenant-a", "another", Priority.URGENT, true);
    var c = coordinator(f, f.registry, new SchedulingQuotaGate(f.gate, g -> Optional.of(Instant.EPOCH)));
    var first = c.claim().orElseThrow();
    assertTrue(c.claim().isEmpty(), "Do not race another local claim before admission");
    assertEquals(0, f.gate.grants());
    assertEquals(0, f.calls.get());
    assertTrue(c.prepare(first).isPresent());
    assertEquals(1, f.gate.grants());
    assertTrue(c.claim().isPresent(), "Preparation releases only the local scheduling slot");
  }

  @Test void oldFailureCannotReleaseReplacementSchedulingOwnership() {
    var f = new CoreFixture(); f.force();
    var c = coordinator(f, f.registry, new SchedulingQuotaGate(f.gate, g -> Optional.of(Instant.EPOCH)));
    var old = c.claim().orElseThrow();
    f.clock.advance(SyncOptions.defaults().lease());
    var current = c.claim().orElseThrow();
    assertNotEquals(old.token(), current.token());
    f.gateway.request("customer", "tenant-a", "another", Priority.URGENT, true);
    c.failed(old, new IllegalStateException("obsolete"));
    assertTrue(c.claim().isEmpty(), "Obsolete work must not clear the replacement slot");
    c.failed(current, new IllegalStateException("current"));
    assertTrue(c.claim().isPresent(), "Failed preparation must release its own slot");
  }

  @Test void unknownBindingPreservesOriginalContractFailure() {
    var f = new CoreFixture();
    var key = new WorkKey("unknown", new SubjectKey("synthetic", "1"));
    var lease = new WorkLease(key, "unknown-token", 1, 0, true, Priority.NORMAL, f.clock.instant().plusSeconds(30));
    assertThrows(IllegalArgumentException.class, () -> f.coordinator.prepare(lease));
  }

  @Test void priorityWheelStillProvidesEveryBucketWithHintedSelection() {
    var f = new CoreFixture();
    for (Priority priority : Priority.values()) for (int i = 0; i < 10; i++)
      f.gateway.request("customer", "tenant-a", priority.name()+i, priority, true);
    var c = coordinator(f, f.registry, new SchedulingQuotaGate(f.gate, g -> Optional.of(Instant.EPOCH)));
    Map<Priority,Integer> counts = new EnumMap<>(Priority.class);
    for (int i = 0; i < 10; i++) {
      var lease = c.claim().orElseThrow();
      counts.merge(lease.priority(), 1, Integer::sum);
      c.failed(lease, new IllegalStateException("release-and-backoff"));
    }
    assertEquals(Map.of(Priority.URGENT,4,Priority.HIGH,3,Priority.NORMAL,2,Priority.BACKGROUND,1),counts);
  }

  @Test void slotRechecksAHintReadBeforeAnotherLocalAdmission() {
    var f = new CoreFixture(); f.force();
    var reads = new AtomicInteger();
    Instant deadline = f.clock.instant().plusSeconds(30);
    var c = coordinator(f, f.registry, new SchedulingQuotaGate(f.gate, group -> {
      if (reads.incrementAndGet() == 1) return Optional.of(Instant.EPOCH);
      f.gate.cooldown(group, deadline); return Optional.of(deadline);
    }));
    assertTrue(c.claim().isEmpty());
    assertEquals(2, reads.get());
    assertEquals("WAITING", f.store.inspect(f.key).status());
    assertEquals(0, f.gate.grants());
    f.clock.advance(Duration.ofSeconds(30));
    assertTrue(c.runOne());
    assertEquals(1, f.calls.get());
  }

  @Test void failedHintRecheckReleasesItsUncommittedSchedulingSlot() {
    var f = new CoreFixture(); f.force();
    var reads = new AtomicInteger();
    var c = coordinator(f, f.registry, new SchedulingQuotaGate(f.gate, group -> {
      if (reads.incrementAndGet() == 2) throw new IllegalStateException("hint read failed");
      return Optional.of(Instant.EPOCH);
    }));
    assertThrows(IllegalStateException.class, c::claim);
    assertTrue(c.claim().isPresent(), "Failed metadata read must not leave a local slot occupied");
    assertEquals(0, f.gate.grants());
  }

  @Test void customStoreIgnoringHintsStillCannotBypassDistributedAdmission() {
    var f = new CoreFixture(); f.force();
    Instant until=f.clock.instant().plusSeconds(30);f.gate.cooldown("owner",until);
    var gate=new SchedulingQuotaGate(f.gate,g->Optional.of(until));
    var c=new SyncCoordinator(f.registry,new LegacyWorkStore(f.store),f.tx,gate,f.clock,
        SyncOptions.defaults(),WeightedPriorities.defaults(),()->.5);
    assertTrue(c.runOne());
    assertEquals(0,f.calls.get());assertEquals(0,f.gate.grants());
  }

  @Test void oneProviderAwaitingPreparationDoesNotReserveAnotherProvider() {
    var f=new CoreFixture();f.force();
    f.gate.register("other-quota",Duration.ofMillis(1));
    var calls=new AtomicInteger();
    var other=ModelBinding.builder("other",String.class).boundary("hint-check")
        .owner("other-owner",r->{calls.incrementAndGet();return FetchResult.found("other",null);})
        .replica(f.projection).repairFromOwner().fingerprint(Digests::sha256Fields).quota("other-quota").build();
    var registry=new ModelRegistry(List.of(f.binding,other));
    var gateway=new SyncGateway(registry,f.store,f.tx,f.clock);
    gateway.request("other","tenant-a","other-key",Priority.BACKGROUND,true);
    var c=coordinator(f,registry,new SchedulingQuotaGate(f.gate,g->Optional.of(Instant.EPOCH)));
    var awaiting=c.claim().orElseThrow();
    assertEquals("customer",awaiting.key().binding());
    assertTrue(c.runOne(),"The unselected provider's local slot must be released immediately");
    assertEquals(1,calls.get());assertEquals(0,f.calls.get());
    c.failed(awaiting,new IllegalStateException("test cleanup"));
  }
}
