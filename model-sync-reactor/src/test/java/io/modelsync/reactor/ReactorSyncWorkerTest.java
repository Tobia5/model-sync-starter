package io.modelsync.reactor;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.*;
import io.modelsync.testkit.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class ReactorSyncWorkerTest {
  @Test
  void blockingOwnerIsIsolatedFromCallerThread() {
    var f = new CoreFixture();
    f.behavior.set(
        request -> {
          assertTrue(Thread.currentThread().getName().startsWith("test-owner"));
          return FetchResult.found("ok", null);
        });
    f.enqueue();
    var persistence = Schedulers.newBoundedElastic(2, 8, "test-jpa");
    var owner = Schedulers.newBoundedElastic(2, 8, "test-owner");
    try {
      var worker =
          new ReactorSyncWorker(f.coordinator, persistence, owner, 2, Duration.ofMillis(10));
      StepVerifier.create(worker.tick()).expectNext(true).verifyComplete();
      assertEquals("ok", f.local().value());
    } finally {
      persistence.dispose();
      owner.dispose();
    }
  }

  @Test
  void gatewayDoesNotExecuteBeforeSubscription() {
    var f = new CoreFixture();
    var persistence = Schedulers.newBoundedElastic(1, 8, "test-jpa");
    try {
      var gateway = new ReactiveSyncGateway(f.gateway, persistence);
      var request = gateway.model(String.class).request("tenant-a", "42", Priority.HIGH, false);
      assertEquals(0, f.store.size());
      StepVerifier.create(request).expectNextCount(1).verifyComplete();
      assertEquals(1, f.store.size());
    } finally {
      persistence.dispose();
    }
  }
}
