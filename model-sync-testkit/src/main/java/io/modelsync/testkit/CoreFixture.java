package io.modelsync.testkit;

import io.modelsync.core.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** String-valued fixture so the entire core contract suite runs without Maven or external jars. */
public final class CoreFixture {
  public final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
  public final MemoryTransactions tx = new MemoryTransactions();
  public final MemoryWorkStore store = new MemoryWorkStore();
  public final MemoryProjectionStore<String> projection = new MemoryProjectionStore<>();
  public final MemoryQuotaGate gate = new MemoryQuotaGate(clock);
  public final AtomicInteger calls = new AtomicInteger();
  public final AtomicReference<ReadRequest> requestSeen = new AtomicReference<>();
  public final AtomicReference<BlockingOwner<String>> behavior =
      new AtomicReference<>(request -> FetchResult.found("source", "\"v1\""));
  public final WorkKey key = new WorkKey("customer", new SubjectKey("tenant-a", "42"));
  public final ModelBinding<String> binding;
  public final ModelRegistry registry;
  public final SyncGateway gateway;
  public final SyncCoordinator coordinator;

  public CoreFixture() {
    this(true, true);
  }

  public CoreFixture(boolean repair, boolean cache) {
    tx.register(store);
    tx.register(projection);
    gate.register("owner", Duration.ofMillis(1));
    var builder =
        ModelBinding.builder("customer", String.class)
            .boundary("cards")
            .owner(
                "external",
                request -> {
                  calls.incrementAndGet();
                  requestSeen.set(request);
                  return behavior.get().fetch(request);
                })
            .fingerprint(value -> Digests.sha256Fields(value))
            .quota("owner")
            .freshness(
                new FreshnessPolicy(
                    Duration.ofSeconds(10), Duration.ofSeconds(60), Duration.ofSeconds(30)));
    if (cache) builder.cache(projection);
    else builder.replica(projection);
    if (repair) builder.repairFromOwner();
    binding = builder.build();
    registry = new ModelRegistry(List.of(binding));
    gateway = new SyncGateway(registry, store, tx, clock);
    coordinator =
        new SyncCoordinator(
            registry,
            store,
            tx,
            gate,
            clock,
            SyncOptions.defaults(),
            WeightedPriorities.defaults(),
            () -> 0.5);
  }

  public void enqueue() {
    gateway.request("customer", "tenant-a", "42", Priority.NORMAL, false);
  }

  public void force() {
    gateway.request("customer", "tenant-a", "42", Priority.URGENT, true);
  }

  public void run() {
    if (!coordinator.runOne()) throw new AssertionError("No ready work");
  }

  public SyncState state() {
    return store.state(key);
  }

  public Projection<String> local() {
    return projection.load(key.subject()).orElseThrow();
  }

  public void advance(long seconds) {
    clock.advance(Duration.ofSeconds(seconds));
  }
}
