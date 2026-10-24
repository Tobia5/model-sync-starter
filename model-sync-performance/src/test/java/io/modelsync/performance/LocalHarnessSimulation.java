package io.modelsync.performance;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.net.URI;

/** The six named simulations share one strictly local instrumented host lifecycle. */
abstract class LocalHarnessSimulation extends Simulation {
  private final String mode;
  private LocalLoadHost host;

  LocalHarnessSimulation(String mode) {
    this.mode = mode;
    String target = System.getenv().getOrDefault("MODELSYNC_LOAD_TARGET", "http://127.0.0.1:18088");
    String name = URI.create(target).getHost();
    if (!"127.0.0.1".equals(name) && !"localhost".equals(name))
      throw new IllegalArgumentException("Local harness requires loopback; remote qualification needs a separately instrumented host");
    ScenarioBuilder scenario = scenario(mode);
    int users = 1;
    switch (mode) {
      case "fresh" -> { users = 40; scenario = scenario.exec(http("fresh visit").get("/visit").check(status().is(200))); }
      case "hot" -> { users = 100; scenario = scenario.exec(http("stale hot visit").get("/visit").check(status().is(202))); }
      case "unsafe" -> { users = 20; scenario = scenario.exec(http("unverified visit").get("/visit").check(status().is(202))); }
      case "event" -> scenario = scenario
          .exec(http("begin owner fetch").post("/claim").check(status().is(202)))
          .exec(http("urgent source event").post("/event").check(status().is(202)))
          .exec(http("passive visit").get("/visit").check(status().is(200)))
          .exec(http("finish prior fetch").post("/finish").check(status().is(202)));
      case "cooldown" -> scenario = scenario
          .exec(http("rate limited job").post("/job").check(status().is(202)))
          .exec(http("advance synthetic clock").post("/advance").check(status().is(202)))
          .exec(http("recovery job").post("/job").check(status().is(202)));
      case "leases" -> { users = 200; scenario = scenario.exec(http("bounded job").post("/job").check(status().is(202))); }
      default -> throw new IllegalArgumentException("Unknown simulation");
    }
    setUp(scenario.injectOpen(atOnceUsers(users)))
        .protocols(http.baseUrl(target))
        .assertions(global().failedRequests().percent().is(0.0));
  }

  @Override public void before() {
    host = new LocalLoadHost(mode);
    try { host.start(); }
    catch (Exception failure) { host.close(); throw new IllegalStateException(failure); }
  }

  @Override public void after() {
    if (host == null) return;
    try { host.assertOutcome(); host.writeReport("PASS"); }
    catch (AssertionError failure) { host.writeReport("FAILED"); throw failure; }
    finally { host.close(); }
  }
}
