package io.modelsync.testkit;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import static io.modelsync.core.DatasetPort.compare;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Executable dependency-free acceptance checks. This is not a substitute for Oracle integration
 * tests.
 */
public final class CoreContracts {
  private static int passed, failed;
  private static final List<String> failures = new ArrayList<>();

  public static void main(String[] args) {
    runAll();
  }

  public static synchronized void runAll() {
    passed = 0;
    failed = 0;
    failures.clear();
    test(
        "subject rejects blank tenant",
        () -> throwsType(IllegalArgumentException.class, () -> new SubjectKey("", "a")));
    test(
        "subject rejects control characters",
        () -> throwsType(IllegalArgumentException.class, () -> new SubjectKey("a", "b\n")));
    test(
        "subject rejects oversized ID",
        () ->
            throwsType(IllegalArgumentException.class, () -> new SubjectKey("a", "x".repeat(257))));
    test(
        "work keys isolate tenants",
        () ->
            notEqual(
                new WorkKey("m", new SubjectKey("a", "1")).storageId(),
                new WorkKey("m", new SubjectKey("b", "1")).storageId()));
    test(
        "work keys isolate model bindings",
        () ->
            notEqual(
                new WorkKey("m", new SubjectKey("a", "1")).storageId(),
                new WorkKey("n", new SubjectKey("a", "1")).storageId()));
    test(
        "work keys use unambiguous length prefixes",
        () ->
            notEqual(
                new WorkKey("a:b", new SubjectKey("c", "d")).storageId(),
                new WorkKey("a", new SubjectKey("b:c", "d")).storageId()));
    test(
        "fingerprint distinguishes null and empty",
        () -> notEqual(Digests.sha256Fields((String) null), Digests.sha256Fields("")));
    test(
        "fingerprint distinguishes concatenation collisions",
        () -> notEqual(Digests.sha256Fields("ab", "c"), Digests.sha256Fields("a", "bc")));
    test(
        "fingerprint is deterministic",
        () -> eq(Digests.sha256Fields("x", "y"), Digests.sha256Fields("x", "y")));
    test(
        "invalid freshness order rejected",
        () ->
            throwsType(
                IllegalArgumentException.class,
                () ->
                    new FreshnessPolicy(
                        Duration.ofDays(2), Duration.ofDays(1), Duration.ofDays(1))));
    test(
        "unknown validation age",
        () -> eq(FreshnessAge.UNKNOWN, FreshnessPolicy.defaults().age(null, Instant.now())));
    test(
        "future validation time is unknown",
        () ->
            eq(
                FreshnessAge.UNKNOWN,
                FreshnessPolicy.defaults().age(Instant.EPOCH.plusSeconds(1), Instant.EPOCH)));
    test(
        "freshness expires at exact max age",
        () -> {
          var p = FreshnessPolicy.defaults();
          eq(FreshnessAge.EXPIRED, p.age(Instant.EPOCH, Instant.EPOCH.plus(p.maxAge())));
        });
    test(
        "refresh is due at exact refresh age",
        () -> {
          var p = FreshnessPolicy.defaults();
          eq(FreshnessAge.STALE, p.age(Instant.EPOCH, Instant.EPOCH.plus(p.refreshAfter())));
        });
    test(
        "incomplete bindings rejected",
        () ->
            throwsType(
                NullPointerException.class, () -> ModelBinding.builder("x", String.class).build()));
    test(
        "duplicate binding IDs rejected",
        () -> {
          var f = new CoreFixture();
          throwsType(
              IllegalArgumentException.class,
              () -> new ModelRegistry(List.of(f.binding, f.binding)));
        });
    test(
        "typed lookup rejects wrong model",
        () -> {
          var f = new CoreFixture();
          throwsType(
              IllegalArgumentException.class, () -> f.registry.get("customer", Integer.class));
        });
    test(
        "unknown route fails before enqueue",
        () -> {
          var f = new CoreFixture();
          throwsType(
              IllegalArgumentException.class,
              () -> f.gateway.request("missing", "a", "1", Priority.NORMAL, false));
          eq(0, f.store.size());
        });
    test(
        "ambiguous class lookup requires a route",
        () -> {
          var f = new CoreFixture();
          var other =
              ModelBinding.builder("other", String.class)
                  .boundary("cards")
                  .owner("x", r -> FetchResult.found("x", null))
                  .cache(f.projection)
                  .quota("owner")
                  .fingerprint(x -> x)
                  .build();
          throwsType(
              IllegalArgumentException.class,
              () -> new ModelRegistry(List.of(f.binding, other)).get(String.class));
        });
    test(
        "boundary catalog includes registered models only",
        () -> {
          var f = new CoreFixture();
          eq(1, f.gateway.boundaryModels("cards").size());
          eq(0, f.gateway.boundaryModels("other").size());
        });
    test(
        "initial owner fetch repairs allowed projection",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          f.run();
          eq("source", f.local().value());
          eq("APPLIED", f.state().outcome());
          eq(f.clock.instant(), f.state().validatedAt());
        });
    test(
        "verify-only never creates local data",
        () -> {
          var f = new CoreFixture(false, false);
          f.enqueue();
          f.run();
          check(f.projection.load(f.key.subject()).isEmpty());
          eq("MISMATCH", f.state().outcome());
          check(f.state().reconciledAt() == null);
        });
    test(
        "durable replica has no cache expiry",
        () -> {
          var f = new CoreFixture(true, false);
          f.enqueue();
          f.run();
          check(f.local().expiresAt() == null);
        });
    test(
        "temporary cache gets separate retention deadline",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          f.run();
          eq(f.clock.instant().plusSeconds(30), f.local().expiresAt());
        });
    test(
        "fresh cache hit does not call owner or reset age",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          f.run();
          Instant verified = f.state().validatedAt();
          long grants = f.gate.grants();
          f.advance(1);
          f.enqueue();
          f.run();
          eq(1, f.calls.get());
          eq(verified, f.state().validatedAt());
          eq(grants, f.gate.grants());
          eq(verified.plusSeconds(10), f.store.inspect(f.key).due());
        });
    test(
        "force refresh bypasses freshness but not quota",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          f.run();
          f.advance(1);
          f.force();
          f.run();
          eq(2, f.calls.get());
        });
    test(
        "fresh local read is local-only",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          f.run();
          check(f.gateway.model(String.class).local("tenant-a", "42").isPresent());
          eq(1, f.calls.get());
        });
    test(
        "expired cache is not returned by local facade",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          f.run();
          f.advance(31);
          check(f.gateway.model(String.class).local("tenant-a", "42").isEmpty());
        });
    test(
        "unchanged owner renews verification and cache TTL",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          f.run();
          f.advance(11);
          f.behavior.set(r -> FetchResult.unchanged(null));
          f.run();
          eq("\"v1\"", f.requestSeen.get().validator());
          eq("NOT_MODIFIED", f.state().outcome());
          eq(f.clock.instant().plusSeconds(30), f.local().expiresAt());
        });
    test(
        "eviction keeps ledger and forces unconditional fetch",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          f.run();
          Instant original = f.state().validatedAt();
          f.projection.evict(f.key.subject());
          eq(original, f.state().validatedAt());
          f.advance(1);
          f.enqueue();
          f.run();
          check(f.requestSeen.get().validator() == null);
          eq("source", f.local().value());
        });
    test(
        "expired payload cannot use an ETag shortcut",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          f.run();
          f.advance(31);
          f.run();
          check(f.requestSeen.get().validator() == null);
        });
    test(
        "local drift disables ETag shortcut",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          f.run();
          f.projection.compareAndSet(f.key.subject(), f.local().revision(), "changed", null);
          f.advance(1);
          f.force();
          f.run();
          check(f.requestSeen.get().validator() == null);
          eq("source", f.local().value());
        });
    test(
        "invalid 304 without cache is quarantined",
        () -> {
          var f = new CoreFixture();
          f.behavior.set(r -> FetchResult.unchanged("etag"));
          f.enqueue();
          f.run();
          eq("DEAD", f.store.inspect(f.key).status());
          eq("UNKNOWN", f.state().outcome());
        });
    test(
        "404 does not delete existing projection",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          f.run();
          Instant matched = f.state().reconciledAt();
          f.advance(11);
          f.behavior.set(r -> FetchResult.absent());
          f.run();
          eq("source", f.local().value());
          eq("OWNER_ABSENT", f.state().outcome());
          eq(matched, f.state().reconciledAt());
        });
    test(
        "fresh mismatch is not marked reconciled",
        () -> {
          var f = new CoreFixture(false, false);
          f.projection.compareAndSet(f.key.subject(), null, "wrong", null);
          f.enqueue();
          f.run();
          eq("MISMATCH", f.state().outcome());
          check(f.state().validatedAt() != null);
          check(f.state().reconciledAt() == null);
          eq("wrong", f.local().value());
        });
    test(
        "failed source call never renews validation",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          f.run();
          Instant old = f.state().validatedAt();
          f.advance(11);
          f.behavior.set(
              r -> {
                throw new java.io.IOException("private source error");
              });
          f.run();
          eq(old, f.state().validatedAt());
          eq(1, f.store.inspect(f.key).attempts());
          eq("FETCH_FAILED", f.store.inspect(f.key).lastCode());
        });
    test(
        "quota denial does not count as source failure",
        () -> {
          var f = new CoreFixture();
          f.gate.cooldown("owner", f.clock.instant().plusSeconds(30));
          f.force();
          f.run();
          eq(0, f.calls.get());
          eq(0, f.store.inspect(f.key).attempts());
          eq(f.clock.instant().plusSeconds(30), f.store.inspect(f.key).due());
        });
    test(
        "provider 429 establishes shared cooldown",
        () -> {
          var f = new CoreFixture();
          Instant until = f.clock.instant().plusSeconds(80);
          f.behavior.set(
              r -> {
                throw new RateLimited(until);
              });
          f.enqueue();
          f.run();
          check(!f.gate.acquire("owner").granted());
          eq(until, f.gate.acquire("owner").retryAt());
          eq(0, f.store.inspect(f.key).attempts());
        });
    test(
        "smooth quota permits one admission per interval",
        () -> {
          var c = new MutableClock(Instant.EPOCH);
          var g = new MemoryQuotaGate(c);
          g.register("q", Duration.ofSeconds(1));
          check(g.acquire("q").granted());
          check(!g.acquire("q").granted());
          c.advance(Duration.ofSeconds(1));
          check(g.acquire("q").granted());
        });
    test(
        "quota configuration conflict fails closed",
        () -> {
          var g = new MemoryQuotaGate(Clock.systemUTC());
          g.register("q", Duration.ofSeconds(1));
          throwsType(IllegalArgumentException.class, () -> g.register("q", Duration.ofSeconds(2)));
        });
    test(
        "unknown quota fails closed",
        () ->
            throwsType(
                NullPointerException.class,
                () -> new MemoryQuotaGate(Clock.systemUTC()).acquire("missing")));
    test(
        "weighted priorities protect background share",
        () -> {
          var wheel = WeightedPriorities.defaults();
          int[] n = new int[4];
          for (int i = 0; i < 1000; i++) n[wheel.next().ordinal()]++;
          eq(List.of(400, 300, 200, 100), Arrays.stream(n).boxed().toList());
        });
    test(
        "zero priority weights rejected",
        () -> throwsType(IllegalArgumentException.class, () -> new WeightedPriorities(4, 3, 2, 0)));
    test(
        "unused priority capacity is borrowed",
        () -> {
          var f = new CoreFixture();
          f.gateway.request("customer", "tenant-a", "42", Priority.BACKGROUND, false);
          f.run();
          eq(1, f.calls.get());
        });
    test(
        "coalesced requests share one durable work item",
        () -> {
          var f = new CoreFixture();
          for (int i = 0; i < 100; i++) f.enqueue();
          eq(1, f.store.size());
          eq(100L, f.store.inspect(f.key).generation());
        });
    test(
        "request during lease is not lost on completion",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          var lease = f.coordinator.claim().orElseThrow();
          var plan = f.coordinator.prepare(lease).orElseThrow();
          f.force();
          f.coordinator.finish(plan, FetchResult.found("one", "v1"));
          var next = f.coordinator.claim().orElseThrow();
          eq(2L, next.generation());
          check(next.force());
          eq(Priority.URGENT, next.priority());
        });
    test(
        "periodic work does not retain an urgent priority forever",
        () -> {
          var f = new CoreFixture();
          f.force();
          f.run();
          eq(Priority.BACKGROUND, f.store.inspect(f.key).priority());
        });
    test(
        "expired lease is reclaimed with a new token",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          var old = f.coordinator.claim().orElseThrow();
          f.advance(61);
          var next = f.coordinator.claim().orElseThrow();
          notEqual(old.token(), next.token());
          check(!f.store.lockValid(old, f.clock.instant()));
        });
    test(
        "stale worker cannot apply its response",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          var old = f.coordinator.claim().orElseThrow();
          var plan = f.coordinator.prepare(old).orElseThrow();
          f.advance(61);
          f.coordinator.claim().orElseThrow();
          throwsType(
              LeaseLost.class,
              () -> f.coordinator.finish(plan, FetchResult.found("late", "v1")));
          check(f.projection.load(f.key.subject()).isEmpty());
        });
    test(
        "stale failure cannot alter a newer lease",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          var old = f.coordinator.claim().orElseThrow();
          f.advance(61);
          var next = f.coordinator.claim().orElseThrow();
          f.coordinator.failed(old, new RuntimeException());
          eq(next.token(), f.store.inspect(f.key).token());
        });
    test(
        "concurrent local revision prevents stale overwrite",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          var lease = f.coordinator.claim().orElseThrow();
          var plan = f.coordinator.prepare(lease).orElseThrow();
          f.projection.compareAndSet(f.key.subject(), null, "new-local", null);
          f.coordinator.finish(plan, FetchResult.found("old-remote", "v1"));
          eq("new-local", f.local().value());
          eq("LOCAL_CONFLICT", f.store.inspect(f.key).lastCode());
          eq("UNKNOWN", f.state().outcome());
        });
    test(
        "CAS conflict is rescheduled without failure penalty",
        () -> {
          var f = new CoreFixture();
          f.projection.rejectNextWrite = true;
          f.enqueue();
          f.run();
          check(f.projection.load(f.key.subject()).isEmpty());
          eq(0, f.store.inspect(f.key).attempts());
          eq("LOCAL_CONFLICT", f.store.inspect(f.key).lastCode());
        });
    test(
        "projection and ledger roll back together",
        () -> {
          var f = new CoreFixture();
          f.store.failNextComplete = true;
          f.enqueue();
          f.run();
          check(f.projection.load(f.key.subject()).isEmpty());
          eq("UNKNOWN", f.state().outcome());
          eq(1, f.store.inspect(f.key).attempts());
        });
    test(
        "business transaction rollback removes new intent",
        () -> {
          var f = new CoreFixture();
          throwsType(
              IllegalStateException.class,
              () ->
                  f.tx.required(
                      () -> {
                        f.enqueue();
                        throw new IllegalStateException();
                      }));
          eq(0, f.store.size());
        });
    test(
        "worker rejects ambient business transaction",
        () -> {
          var f = new CoreFixture();
          throwsType(
              IllegalStateException.class, () -> f.tx.required(() -> f.coordinator.runOne()));
        });
    test(
        "retry exhaustion moves work to dead state",
        () -> {
          var f = new CoreFixture();
          f.behavior.set(
              r -> {
                throw new IllegalStateException();
              });
          f.enqueue();
          for (int i = 0; i < 8; i++) {
            f.run();
            f.advance(601);
          }
          eq("DEAD", f.store.inspect(f.key).status());
          check(!f.coordinator.runOne());
        });
    test(
        "explicit new request revives dead work",
        () -> {
          var f = new CoreFixture();
          f.behavior.set(
              r -> {
                throw new Permanent("DENIED");
              });
          f.enqueue();
          f.run();
          eq("DEAD", f.store.inspect(f.key).status());
          f.advance(1);
          f.behavior.set(r -> FetchResult.found("ok", null));
          f.force();
          f.run();
          eq("ok", f.local().value());
          eq(0, f.store.inspect(f.key).attempts());
        });
    test(
        "lease duration is validated against fetch timeout",
        () ->
            throwsType(
                IllegalArgumentException.class,
                () ->
                    new SyncOptions(
                        Duration.ofSeconds(20),
                        Duration.ofSeconds(15),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(5),
                        2)));
    test(
        "async owners work without Reactor in core",
        () -> {
          var f = new CoreFixture();
          var b =
              ModelBinding.builder("async", String.class)
                  .boundary("cards")
                  .asyncOwner(
                      "provider",
                      r -> CompletableFuture.completedFuture(FetchResult.found("async-data", "v")))
                  .cache(f.projection)
                  .fingerprint(v -> v)
                  .quota("owner")
                  .repairFromOwner()
                  .build();
          var registry = new ModelRegistry(List.of(b));
          var engine =
              new SyncCoordinator(
                  registry,
                  f.store,
                  f.tx,
                  f.gate,
                  f.clock,
                  SyncOptions.defaults(),
                  WeightedPriorities.defaults(),
                  () -> 0.5);
          new SyncGateway(registry, f.store, f.tx, f.clock)
              .request("async", "t", "1", Priority.NORMAL, false);
          check(engine.runOne());
          eq("async-data", f.projection.load(new SubjectKey("t", "1")).orElseThrow().value());
        });
    test(
        "matching dataset manifests reconcile",
        () ->
            eq(
                DatasetComparison.MATCH,
                compare(manifest("s", 10, 0, "abc", "12.00"), manifest("s", 10, 0, "abc", "12"))));
    test(
        "dataset snapshot mismatch is incomparable",
        () ->
            eq(
                DatasetComparison.INCOMPARABLE,
                compare(manifest("s1", 10, 0, "abc", "12"), manifest("s2", 10, 0, "abc", "12"))));
    test(
        "unresolved dataset records prevent a pass",
        () ->
            eq(
                DatasetComparison.INCOMPLETE,
                compare(manifest("s", 10, 1, "abc", "12"), manifest("s", 10, 0, "abc", "12"))));
    test(
        "equal counts do not conceal changed content",
        () ->
            eq(
                DatasetComparison.DIFFERENT,
                compare(manifest("s", 10, 0, "abc", "12"), manifest("s", 10, 0, "def", "12"))));
    test(
        "missing dataset checksum is incomplete",
        () ->
            eq(
                DatasetComparison.INCOMPLETE,
                compare(manifest("s", 10, 0, null, "12"), manifest("s", 10, 0, null, "12"))));
    test(
        "different business totals are detected",
        () ->
            eq(
                DatasetComparison.DIFFERENT,
                compare(manifest("s", 10, 0, "abc", "12"), manifest("s", 10, 0, "abc", "13"))));
    test(
        "dataset row count difference is detected",
        () ->
            eq(
                DatasetComparison.DIFFERENT,
                compare(manifest("s", 10, 0, "abc", "12"), manifest("s", 11, 0, "abc", "12"))));
    test(
        "negative dataset count is rejected",
        () -> throwsType(IllegalArgumentException.class, () -> manifest("s", -1, 0, "a", "1")));
    test(
        "concurrent callers coalesce without losing generations",
        () -> {
          var f = new CoreFixture();
          try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 200; i++) futures.add(pool.submit(f::enqueue));
            for (var future : futures) {
              try {
                future.get(5, TimeUnit.SECONDS);
              } catch (Exception e) {
                throw new AssertionError(e);
              }
            }
          }
          eq(1, f.store.size());
          eq(200L, f.store.inspect(f.key).generation());
        });
    test(
        "quota allows only one concurrent admission",
        () -> {
          var c = new MutableClock(Instant.EPOCH);
          var g = new MemoryQuotaGate(c);
          g.register("q", Duration.ofSeconds(1));
          var n = new java.util.concurrent.atomic.AtomicInteger();
          try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < 100; i++)
              fs.add(
                  pool.submit(
                      () -> {
                        if (g.acquire("q").granted()) n.incrementAndGet();
                      }));
            for (var future : fs) {
              try {
                future.get(5, TimeUnit.SECONDS);
              } catch (Exception e) {
                throw new AssertionError(e);
              }
            }
          }
          eq(1, n.get());
        });
    test(
        "maxAge blocks serving a non-expiring replica",
        () -> {
          var f = new CoreFixture(true, false);
          f.enqueue();
          f.run();
          f.advance(61);
          check(f.gateway.model(String.class).local("tenant-a", "42").isEmpty());
          check(f.gateway.model(String.class).inspect("tenant-a", "42").isPresent());
        });
    test(
        "strict local read rejects stale but bounded cache",
        () -> {
          var f = new CoreFixture();
          f.enqueue();
          f.run();
          f.advance(11);
          check(f.gateway.model(String.class).local("tenant-a", "42").isPresent());
          check(f.gateway.model(String.class).freshLocal("tenant-a", "42").isEmpty());
        });
    test(
        "unverified local projection is diagnostic only",
        () -> {
          var f = new CoreFixture();
          f.projection.compareAndSet(f.key.subject(), null, "unverified", null);
          check(f.gateway.model(String.class).local("tenant-a", "42").isEmpty());
          eq(
              FreshnessAge.UNKNOWN,
              f.gateway.model(String.class).inspect("tenant-a", "42").orElseThrow().age());
        });
    test(
        "relative provider cooldown uses the coordinator clock",
        () -> {
          var f = new CoreFixture();
          f.behavior.set(
              r -> {
                throw new RateLimitedAfter(Duration.ofSeconds(90));
              });
          f.enqueue();
          f.run();
          eq(f.clock.instant().plusSeconds(90), f.store.inspect(f.key).due());
          eq(0, f.store.inspect(f.key).attempts());
        });
    test(
        "blank dataset checksums cannot pass",
        () ->
            eq(
                DatasetComparison.INCOMPLETE,
                compare(manifest("s", 1, 0, "", "1"), manifest("s", 1, 0, "", "1"))));
    test(
        "dataset identity cannot be inferred from null fields",
        () ->
            throwsType(
                IllegalArgumentException.class, () -> new DatasetSlice("d", "t", null, "p", "v", "g")));
    test(
        "null owner response is a permanent adapter error",
        () -> {
          var f = new CoreFixture();
          f.behavior.set(request -> null);
          f.enqueue();
          f.run();
          eq("DEAD", f.store.inspect(f.key).status());
          eq("NULL_FETCH", f.store.inspect(f.key).lastCode());
        });
    test(
        "header validators cannot contain line breaks",
        () ->
            throwsType(
                Permanent.class, () -> FetchResult.found("x", "etag\r\nheader")));
    System.out.println("CORE CONTRACTS: " + passed + " passed, " + failed + " failed");
    if (failed != 0) throw new AssertionError(String.join("; ", failures));
  }

  private static DatasetManifest manifest(
      String snapshot, long rows, long missing, String digest, String total) {
    return new DatasetManifest(
        new DatasetSlice("customers", "tenant", snapshot, "p1", "v1", "customer"),
        rows,
        missing,
        Map.of("amount", new BigDecimal(total)),
        digest);
  }

  private static void test(String name, Runnable work) {
    try {
      work.run();
      passed++;
      System.out.println("PASS " + name);
    } catch (Throwable e) {
      failed++;
      failures.add(name + ": " + e);
      System.out.println("FAIL " + name + ": " + e);
    }
  }

  private static void check(boolean value) {
    if (!value) throw new AssertionError("Condition not met");
  }

  private static void eq(Object expected, Object actual) {
    if (!Objects.equals(expected, actual))
      throw new AssertionError("Expected " + expected + ", got " + actual);
  }

  private static void notEqual(Object a, Object b) {
    if (Objects.equals(a, b)) throw new AssertionError("Values unexpectedly equal");
  }

  private static void throwsType(Class<? extends Throwable> type, Runnable work) {
    try {
      work.run();
    } catch (Throwable e) {
      if (type.isInstance(e)) return;
      throw new AssertionError("Wrong exception: " + e, e);
    }
    throw new AssertionError("Expected " + type.getSimpleName());
  }
}
