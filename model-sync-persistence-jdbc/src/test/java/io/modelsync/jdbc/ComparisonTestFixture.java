package io.modelsync.jdbc;

import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.*;
import io.modelsync.testkit.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Cheap real-orchestration fixture using the explicitly test-only transactional memory stores. */
final class ComparisonTestFixture {
  final ComparisonAccounting accounting = new ComparisonAccounting();
  final ScriptedQuotaGate scripted = new ScriptedQuotaGate();
  final MeasuredQuotaGate quota = new MeasuredQuotaGate(scripted, false, accounting);
  final MemoryTransactions transactions = new MemoryTransactions();
  final MemoryWorkStore durable = new MemoryWorkStore();
  final MemoryProjectionStore<String> projection = new MemoryProjectionStore<>();
  final AccountingFaultStore faults = new AccountingFaultStore(durable);
  final AtomicLong physical = new AtomicLong();
  final Clock clock = Clock.fixed(Instant.parse("2026-10-25T00:00:00Z"), ZoneOffset.UTC);
  final WorkKey key = new WorkKey("accounting", new SubjectKey("synthetic", "one"));
  final SyncCoordinator coordinator;

  ComparisonTestFixture() { this(request -> FetchResult.found("value", "v1")); }
  ComparisonTestFixture(BlockingOwner<String> owner) {
    transactions.register(durable); transactions.register(projection);
    var tx = new ObservedLocalTransactions(transactions, accounting);
    var store = new ObservedWorkStore(faults, tx);
    var model = ModelBinding.builder("accounting", String.class).boundary("synthetic-accounting")
        .owner("local", new ObservedOwner<>(request -> { physical.incrementAndGet(); return owner.fetch(request); }, accounting))
        .replica(projection).repairFromOwner().fingerprint(Digests::sha256Fields).quota("test-quota")
        .freshness(new FreshnessPolicy(Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofHours(1))).build();
    coordinator = new SyncCoordinator(new ModelRegistry(List.of(model)), store, tx, quota, clock,
        SyncOptions.defaults(), WeightedPriorities.defaults(), () -> .5);
    enqueue(); accounting.begin("measurement", quota);
  }
  void enqueue() { transactions.required(() -> durable.enqueue(key, Priority.NORMAL, false, clock.instant())); }
  Map<String, Object> finish() { return accounting.finish(quota); }
}
