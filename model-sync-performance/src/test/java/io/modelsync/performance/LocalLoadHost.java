package io.modelsync.performance;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.BlockingOwner;
import io.modelsync.testkit.CoreFixture;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Isolated local API harness; its memory store is never production persistence evidence. */
public final class LocalLoadHost implements AutoCloseable {
  private final String mode;
  private final CoreFixture fixture = new CoreFixture();
  private final SyncTriggers triggers = new SyncTriggers(fixture.gateway);
  private final SyncAccess<String> access = triggers.model("customer", String.class);
  private final SyncJob job = new SyncJob(fixture.coordinator);
  private final AtomicInteger visits = new AtomicInteger();
  private final AtomicInteger jobs = new AtomicInteger();
  private final List<WorkKey> ledgerKeys = new ArrayList<>();
  private HttpServer server;
  private ExecutorService executor;
  private PreparedSync<?> inFlight;
  private Instant firstValidated;
  private int firstOwnerCalls;
  private long startedNanos;

  public LocalLoadHost(String mode) { this.mode = mode; }

  public void start() throws IOException {
    startedNanos = System.nanoTime();
    if (mode.equals("fresh") || mode.equals("hot") || mode.equals("event")) {
      fixture.enqueue(); fixture.run();
      firstValidated = fixture.state().validatedAt();
      firstOwnerCalls = fixture.calls.get();
      if (mode.equals("hot")) fixture.clock.advance(Duration.ofSeconds(10));
      if (mode.equals("event")) fixture.clock.advance(Duration.ofMillis(1));
    } else if (mode.equals("unsafe")) {
      fixture.projection.compareAndSet(fixture.key.subject(), null, "unverified", null);
    } else if (mode.equals("cooldown")) {
      fixture.behavior.set(request -> {
        if (fixture.calls.get() == 1) throw new RateLimitedAfter(Duration.ofSeconds(30));
        return FetchResult.found("recovered", "v2");
      });
      fixture.enqueue();
    } else if (mode.equals("leases")) {
      for (int i = 0; i < 200; i++) {
        WorkKey key = new WorkKey("customer", new SubjectKey("tenant-a", Integer.toString(i)));
        ledgerKeys.add(key);
        fixture.gateway.request("customer", "tenant-a", Integer.toString(i), Priority.NORMAL, false);
      }
    }
    try {
      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 18088), 16);
      executor = Executors.newFixedThreadPool(8);
      server.setExecutor(executor);
      server.createContext("/visit", this::visit);
      server.createContext("/event", e -> respond(e, () -> {
        triggers.accept(SyncRequested.changed("customer", "tenant-a", "42")); return "accepted";
      }));
      server.createContext("/claim", e -> respond(e, () -> {
        triggers.accept(SyncRequested.changed("customer", "tenant-a", "42"));
        WorkLease lease = fixture.coordinator.claim().orElseThrow();
        inFlight = fixture.coordinator.prepare(lease).orElseThrow();
        return "claimed";
      }));
      server.createContext("/finish", e -> respond(e, () -> {
        PreparedSync<?> prepared = inFlight;
        if (prepared == null) throw new IllegalStateException("No in-flight fetch");
        @SuppressWarnings("unchecked") BlockingOwner<String> owner = (BlockingOwner<String>) prepared.binding().owner();
        try { fixture.coordinator.finish(prepared, owner.fetch(prepared.request())); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
        return "finished";
      }));
      server.createContext("/advance", e -> respond(e, () -> {
        fixture.clock.advance(Duration.ofSeconds(31)); return "advanced";
      }));
      server.createContext("/job", e -> respond(e, () -> {
        if (mode.equals("leases")) fixture.clock.advance(Duration.ofMillis(1));
        jobs.incrementAndGet();
        // Independent worker instances may overlap; a single SyncJob intentionally rejects overlap.
        SyncJob worker = mode.equals("leases") ? new SyncJob(fixture.coordinator) : job;
        worker.run(1, Duration.ofSeconds(1)); return "accepted";
      }));
      server.createContext("/stats", e -> respond(e, () ->
          "visits=" + visits.get() + ",jobs=" + jobs.get() + ",ownerCalls=" + fixture.calls.get()
              + ",ledger=" + fixture.store.size()));
      server.start();
    } catch (RuntimeException | IOException failure) {
      close(); throw failure;
    }
  }

  private void visit(HttpExchange exchange) throws IOException {
    visits.incrementAndGet();
    VisitResult<String> result = access.visit("tenant-a", "42", VisitPolicy.REQUIRE_FRESH);
    send(exchange, result.projection().isPresent() ? 200 : 202,
        result.projection().isPresent() ? "verified" : "pending");
  }

  private void respond(HttpExchange exchange, java.util.concurrent.Callable<String> work) throws IOException {
    try { send(exchange, 202, work.call()); }
    catch (Exception failure) { send(exchange, 500, "harness failure"); }
  }

  private void send(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    try (var out = exchange.getResponseBody()) { out.write(bytes); }
  }

  public void assertOutcome() {
    if (mode.equals("fresh")) {
      require(visits.get() > 0 && fixture.calls.get() == firstOwnerCalls, "Fresh visits fetched owner");
      require(fixture.state().validatedAt().equals(firstValidated), "Fresh visits renewed source age");
    } else if (mode.equals("hot")) {
      require(visits.get() > 0 && fixture.store.size() == 1, "Hot visits did not coalesce");
      require(fixture.calls.get() == firstOwnerCalls, "Passive visits fetched owner");
      require(fixture.store.inspect(fixture.key).generation() <= 2, "Hot visits created generations");
    } else if (mode.equals("unsafe")) {
      require(visits.get() > 0 && fixture.calls.get() == 0, "Unverified visit fetched owner");
      require(!fixture.state().matched(), "Unverified projection claimed fresh");
    } else if (mode.equals("event")) {
      require(inFlight != null && fixture.store.inspect(fixture.key).generation() > 1,
          "Source change during fetch did not survive");
      require(fixture.store.inspect(fixture.key).status().equals("WAITING"), "Urgent work was lost");
    } else if (mode.equals("cooldown")) {
      require(fixture.calls.get() == 2 && fixture.state().matched(), "Cooldown/recovery outcome absent");
      require(jobs.get() >= 2, "Cooldown job requests absent");
    } else if (mode.equals("leases")) {
      long completed = ledgerKeys.stream().filter(k -> fixture.store.state(k).matched()).count();
      require(jobs.get() > 1 && completed >= 10 && completed == fixture.calls.get(),
          "Ledger completions lack owner-call evidence");
      require(fixture.store.size() == ledgerKeys.size(), "Synthetic ledger count drifted");
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  public void writeReport(String status) {
    try {
      Path dir = Path.of("target", "qm"); Files.createDirectories(dir);
      long completed = ledgerKeys.stream().filter(k -> fixture.store.state(k).matched()).count();
      String json = "{\"scenario\":\"" + mode + "\",\"status\":\"" + status
          + "\",\"accepted_visits\":" + visits.get() + ",\"job_requests\":" + jobs.get()
          + ",\"physical_owner_calls\":" + fixture.calls.get() + ",\"ledger_rows\":" + fixture.store.size()
          + ",\"completed_rows\":" + completed + ",\"synthetic_rows\":" + ledgerKeys.size()
          + ",\"warmup_ms\":0,\"measurement_and_drain_ms\":"
          + ((System.nanoTime() - startedNanos) / 1_000_000)
          + ",\"jvm_max_heap_bytes\":" + Runtime.getRuntime().maxMemory()
          + ",\"available_processors\":" + Runtime.getRuntime().availableProcessors() + "}\n";
      Files.writeString(dir.resolve("load-" + mode + ".json"), json, StandardCharsets.UTF_8);
    } catch (IOException error) { throw new IllegalStateException("Cannot write local load evidence", error); }
  }

  @Override public void close() {
    if (server != null) server.stop(0);
    if (executor != null) executor.shutdownNow();
  }
}
