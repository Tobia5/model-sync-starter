package io.modelsync.testkit;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Dependency-free trigger and recurrence scenarios; not a substitute for Oracle/Spring integration tests. */
public final class TriggerContracts {
  private static int passed;

  private static TriggerFixture fixture(RefreshMode mode) {
    return fixture(mode, true, new FreshnessPolicy(Duration.ofSeconds(10), Duration.ofSeconds(60), Duration.ofSeconds(120)));
  }
  @SuppressWarnings("unchecked")
  private static TriggerFixture fixture(RefreshMode mode, boolean repair, FreshnessPolicy freshness) {
    var f = new CoreFixture();
    var builder = ModelBinding.builder("customer", String.class).boundary("cards")
        .owner("external", (BlockingOwner<String>) f.binding.owner()).cache(f.projection)
        .fingerprint(Digests::sha256Fields).quota("owner").freshness(freshness).refreshMode(mode);
    if (repair) builder.repairFromOwner();
    var b = builder.build();
    var registry = new ModelRegistry(List.of(b));
    var gateway = new SyncGateway(registry, f.store, f.tx, f.clock);
    var coordinator = new SyncCoordinator(registry, f.store, f.tx, f.gate, f.clock,
        SyncOptions.defaults(), WeightedPriorities.defaults(), () -> 0.5);
    var triggers = new SyncTriggers(gateway);
    return new TriggerFixture(f, b, coordinator, triggers, gateway.model(String.class), triggers.model(String.class));
  }

  public static void main(String[] args) { runAll(); }
  public static void runAll() {
    passed = 0;
    test("default recurrence remains PERIODIC", () -> eq(RefreshMode.PERIODIC, new CoreFixture().binding.refreshMode()));
    test("null recurrence rejected", () -> throwsType(NullPointerException.class, () -> ModelBinding.builder("x", String.class).refreshMode(null)));
    test("missing visit queues but does not call owner", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); var result = f.visit(VisitPolicy.STALE_WHILE_REVALIDATE);
      check(result.pending()); eq(FreshnessAge.UNKNOWN, result.age()); eq(0, f.base().calls.get());
    });
    test("hot missing visits coalesce without generation growth", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); for (int i = 0; i < 1000; i++) f.visit(VisitPolicy.REQUIRE_FRESH);
      eq(1, f.base().store.size()); eq(1L, f.base().store.inspect(f.key()).generation());
    });
    test("visits do not add a generation to an active lease", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.visit(VisitPolicy.REQUIRE_FRESH);
      var lease = f.coordinator().claim().orElseThrow(); f.coordinator().prepare(lease).orElseThrow();
      for (int i = 0; i < 100; i++) f.visit(VisitPolicy.REQUIRE_FRESH);
      eq(lease.generation(), f.base().store.inspect(f.key()).generation());
    });
    test("fresh visit performs no enqueue or remote fetch", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.seed(); long generation = f.base().store.inspect(f.key()).generation();
      var result = f.visit(VisitPolicy.REQUIRE_FRESH); check(result.projection().isPresent()); check(!result.refreshRequested());
      eq(1, f.base().calls.get()); eq(generation, f.base().store.inspect(f.key()).generation());
    });
    test("visits do not slide validation or retention", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.seed(); var state = f.base().state(); var expires = f.base().local().expiresAt();
      f.base().advance(9); f.visit(VisitPolicy.STALE_WHILE_REVALIDATE);
      eq(state.validatedAt(), f.base().state().validatedAt()); eq(expires, f.base().local().expiresAt());
    });
    test("exact refresh threshold serves bounded stale and queues", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.seed(); f.base().advance(10); var r = f.visit(VisitPolicy.STALE_WHILE_REVALIDATE);
      eq(FreshnessAge.STALE, r.age()); check(r.projection().isPresent()); check(r.refreshRequested());
    });
    test("strict visit withholds stale projection", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.seed(); f.base().advance(10); check(f.visit(VisitPolicy.REQUIRE_FRESH).pending());
    });
    test("exact maximum age withholds projection", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.seed(); f.base().advance(60); var r = f.visit(VisitPolicy.STALE_WHILE_REVALIDATE);
      eq(FreshnessAge.EXPIRED, r.age()); check(r.pending());
    });
    test("physical expiry overrides otherwise fresh validation", () -> {
      var f = fixture(RefreshMode.ON_DEMAND, true, new FreshnessPolicy(Duration.ofSeconds(40), Duration.ofSeconds(80), Duration.ofSeconds(5)));
      f.seed(); f.base().advance(5); var r = f.visit(VisitPolicy.STALE_WHILE_REVALIDATE);
      eq(FreshnessAge.EXPIRED, r.age()); check(r.projection().isEmpty());
    });
    test("local digest drift cannot be served by visit", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.seed(); f.base().projection.compareAndSet(f.key().subject(), f.base().local().revision(), "unverified", null);
      var r = f.visit(VisitPolicy.STALE_WHILE_REVALIDATE); eq(FreshnessAge.UNKNOWN, r.age()); check(r.pending());
    });
    test("verify-only mismatch remains unavailable", () -> {
      var f = fixture(RefreshMode.ON_DEMAND, false, FreshnessPolicy.defaults());
      f.base().projection.compareAndSet(f.key().subject(), null, "different", null); f.seed();
      eq("MISMATCH", f.base().state().outcome()); check(f.visit(VisitPolicy.STALE_WHILE_REVALIDATE).projection().isEmpty());
    });
    test("owner absence is not local deletion or success", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.seed(); f.base().advance(1);
      f.base().behavior.set(r -> FetchResult.absent()); f.triggers().accept(SyncRequested.changed("customer", "tenant-a", "42")); f.run();
      eq("source", f.base().local().value()); eq("OWNER_ABSENT", f.base().state().outcome()); check(f.visit(VisitPolicy.STALE_WHILE_REVALIDATE).projection().isEmpty());
    });
    test("on-demand work remains idle without visits", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.seed(); eq("IDLE", f.base().store.inspect(f.key()).status());
      f.base().advance(100000); check(!f.coordinator().runOne()); eq(1, f.base().calls.get());
    });
    test("periodic mode remains due without visits", () -> {
      var f = fixture(RefreshMode.PERIODIC); f.seed(); f.base().advance(10); f.run(); eq(2, f.base().calls.get());
    });
    test("fresh explicit request parks on-demand without renewing evidence", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.seed(); var old = f.base().state(); f.base().advance(1);
      f.model().request("tenant-a", "42", Priority.NORMAL); f.run(); eq("IDLE", f.base().store.inspect(f.key()).status());
      eq(1, f.base().calls.get()); eq(old, f.base().state());
    });
    test("source event bypasses fresh shortcut", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.seed(); f.base().advance(1); f.triggers().accept(SyncRequested.changed("customer", "tenant-a", "42")); f.run();
      eq(2, f.base().calls.get());
    });
    test("event during fetch survives idle completion", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.visit(VisitPolicy.REQUIRE_FRESH);
      var lease = f.coordinator().claim().orElseThrow(); var plan = f.coordinator().prepare(lease).orElseThrow();
      f.triggers().accept(SyncRequested.changed("customer", "tenant-a", "42"));
      f.coordinator().finish(plan, FetchResult.found("first", "v1"));
      eq("WAITING", f.base().store.inspect(f.key()).status()); var next = f.coordinator().claim().orElseThrow(); check(next.force()); eq(2L, next.generation());
    });
    test("ordinary visits do not create a follow-up fetch", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.visit(VisitPolicy.REQUIRE_FRESH);
      var lease = f.coordinator().claim().orElseThrow(); var plan = f.coordinator().prepare(lease).orElseThrow();
      f.visit(VisitPolicy.REQUIRE_FRESH); f.coordinator().finish(plan, FetchResult.found("ok", "v1"));
      eq("IDLE", f.base().store.inspect(f.key()).status());
    });
    test("event enqueue rolls back with business transaction", () -> {
      var f = fixture(RefreshMode.ON_DEMAND);
      throwsType(IllegalStateException.class, () -> f.base().tx.required(() -> { f.triggers().accept(SyncRequested.changed("customer", "tenant-a", "42")); throw new IllegalStateException(); }));
      eq(0, f.base().store.size());
    });
    test("hot visits cannot bypass failure backoff", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.base().behavior.set(r -> { throw new IllegalStateException(); });
      f.visit(VisitPolicy.REQUIRE_FRESH); f.run(); var row = f.base().store.inspect(f.key());
      for (int i = 0; i < 100; i++) f.visit(VisitPolicy.REQUIRE_FRESH);
      eq(row.due(), f.base().store.inspect(f.key()).due()); eq(row.generation(), f.base().store.inspect(f.key()).generation());
      eq(row.attempts(), f.base().store.inspect(f.key()).attempts()); check(!f.coordinator().runOne());
    });
    test("hot visits cannot bypass quota cooldown", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.base().gate.cooldown("owner", f.base().clock.instant().plusSeconds(30));
      f.visit(VisitPolicy.REQUIRE_FRESH); f.run(); var due = f.base().store.inspect(f.key()).due();
      f.visit(VisitPolicy.REQUIRE_FRESH); eq(due, f.base().store.inspect(f.key()).due()); check(!f.coordinator().runOne()); eq(0, f.base().calls.get());
    });
    test("visit cannot resurrect DEAD work", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.base().behavior.set(r -> { throw new Permanent("BAD_MAPPING"); });
      f.visit(VisitPolicy.REQUIRE_FRESH); f.run(); var r = f.visit(VisitPolicy.REQUIRE_FRESH);
      eq("DEAD", f.base().store.inspect(f.key()).status()); check(r.refreshSuppressed()); check(!r.pending()); check(r.projection().isEmpty());
    });
    test("explicit change event can reactivate DEAD after repair", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.base().behavior.set(r -> { throw new Permanent("BAD_MAPPING"); });
      f.visit(VisitPolicy.REQUIRE_FRESH); f.run(); f.base().behavior.set(r -> FetchResult.found("fixed", "v2")); f.base().advance(1);
      f.triggers().accept(SyncRequested.changed("customer", "tenant-a", "42")); f.run(); eq("IDLE", f.base().store.inspect(f.key()).status()); eq("fixed", f.base().local().value());
    });
    test("on-demand transient failure retains retry", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.base().behavior.set(r -> { throw new IllegalStateException(); });
      f.visit(VisitPolicy.REQUIRE_FRESH); f.run(); eq("WAITING", f.base().store.inspect(f.key()).status()); eq(1, f.base().store.inspect(f.key()).attempts());
    });
    test("on-demand projection and ledger roll back atomically", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.base().store.failNextComplete = true;
      f.visit(VisitPolicy.REQUIRE_FRESH); f.run(); check(f.base().projection.load(f.key().subject()).isEmpty()); eq("UNKNOWN", f.base().state().outcome());
    });
    test("expired worker cannot park a reclaimed lease", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.visit(VisitPolicy.REQUIRE_FRESH); var old = f.coordinator().claim().orElseThrow();
      var plan = f.coordinator().prepare(old).orElseThrow(); f.base().advance(61); var current = f.coordinator().claim().orElseThrow();
      throwsType(LeaseLost.class, () -> f.coordinator().finish(plan, FetchResult.found("late", "v1")));
      eq(current.token(), f.base().store.inspect(f.key()).token());
    });
    test("tenant identities do not coalesce", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.access().visit("tenant-a", "42", VisitPolicy.REQUIRE_FRESH); f.access().visit("tenant-b", "42", VisitPolicy.REQUIRE_FRESH); eq(2, f.base().store.size());
    });
    test("unregistered event binding fails before enqueue", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); throwsType(IllegalArgumentException.class, () -> f.triggers().accept(SyncRequested.changed("missing", "tenant-a", "42"))); eq(0, f.base().store.size());
    });
    test("invalid visit arguments rejected", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); throwsType(NullPointerException.class, () -> f.visit(null));
      throwsType(IllegalArgumentException.class, () -> f.access().visit("", "42", VisitPolicy.REQUIRE_FRESH)); eq(0, f.base().store.size());
    });
    test("result cannot carry expired data", () -> throwsType(IllegalArgumentException.class, () -> new VisitResult<>(Optional.of(new Projection<>("x", "1", null)), FreshnessAge.EXPIRED, Optional.empty())));
    test("job empty result is truthful", () -> { var f = fixture(RefreshMode.ON_DEMAND); eq(new SyncJobResult(0, SyncJobStopReason.EMPTY), new SyncJob(f.coordinator()).run(10, Duration.ofSeconds(1))); });
    test("job limits count attempts not successful syncs", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); for (int i = 0; i < 10; i++) f.triggers().accept(SyncRequested.changed("customer", "tenant-a", Integer.toString(i)));
      var r = new SyncJob(f.coordinator()).run(3, Duration.ofSeconds(10)); eq(3, r.attempted()); eq(SyncJobStopReason.ITEM_LIMIT, r.stopReason());
      eq(1, f.base().calls.get());
    });
    test("job time budget uses monotonic ticker", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); f.visit(VisitPolicy.REQUIRE_FRESH); var n = new java.util.concurrent.atomic.AtomicLong();
      var r = new SyncJob(f.coordinator(), () -> n.getAndAdd(10)).run(1, Duration.ofNanos(5));
      eq(SyncJobStopReason.TIME_BUDGET, r.stopReason()); eq(0, f.base().calls.get());
    });
    test("job honors interruption without clearing it", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); Thread.currentThread().interrupt();
      try { eq(SyncJobStopReason.INTERRUPTED, new SyncJob(f.coordinator()).run(1, Duration.ofSeconds(1)).stopReason()); check(Thread.currentThread().isInterrupted()); }
      finally { Thread.interrupted(); }
    });
    test("overlapping same-instance job is rejected", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); var job = new SyncJob(f.coordinator()); var inner = new AtomicReference<SyncJobResult>();
      f.base().behavior.set(r -> { inner.set(job.run(1, Duration.ofSeconds(1))); return FetchResult.found("x", "v1"); });
      f.visit(VisitPolicy.REQUIRE_FRESH); job.run(1, Duration.ofSeconds(1)); eq(SyncJobStopReason.ALREADY_RUNNING, inner.get().stopReason());
    });
    test("invalid job bounds fail before claiming", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); var job = new SyncJob(f.coordinator());
      throwsType(IllegalArgumentException.class, () -> job.run(0, Duration.ofSeconds(1)));
      throwsType(IllegalArgumentException.class, () -> job.run(1, Duration.ZERO));
    });
    test("non-idle custom store rejected for on-demand binding", () -> {
      var f = fixture(RefreshMode.ON_DEMAND); var unsupported = new LegacyWorkStore(f.base().store);
      throwsType(IllegalStateException.class, () -> new SyncCoordinator(new ModelRegistry(List.of(f.binding())), unsupported, f.base().tx, f.base().gate, f.base().clock,
          SyncOptions.defaults(), WeightedPriorities.defaults(), () -> 0.5));
    });
    test("legacy custom store remains valid for periodic binding", () -> {
      var f = fixture(RefreshMode.PERIODIC); new SyncCoordinator(new ModelRegistry(List.of(f.binding())), new LegacyWorkStore(f.base().store), f.base().tx, f.base().gate, f.base().clock,
          SyncOptions.defaults(), WeightedPriorities.defaults(), () -> 0.5);
    });
    test("custom store without safe visit admission fails closed", () -> {
      var f = fixture(RefreshMode.PERIODIC); var legacy = new LegacyWorkStore(f.base().store);
      throwsType(IllegalStateException.class, () -> legacy.enqueueRefresh(f.key(), Priority.NORMAL, f.base().clock.instant()));
    });
    System.out.println("TRIGGER CONTRACTS: " + passed + " passed, 0 failed");
  }

  private static void test(String name, Runnable test) { try { test.run(); passed++; System.out.println("PASS " + name); } catch (Throwable e) { throw new AssertionError(name, e); } }
  private static void check(boolean value) { if (!value) throw new AssertionError("Condition not met"); }
  private static void eq(Object expected, Object actual) { if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + " but got " + actual); }
  private static void throwsType(Class<? extends Throwable> type, Runnable action) { try { action.run(); } catch (Throwable e) { if (type.isInstance(e)) return; throw new AssertionError("Wrong exception",e); } throw new AssertionError("Expected " + type.getName()); }
}
