package io.modelsync.reactor;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.FetchResult;
import io.modelsync.testkit.CoreFixture;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;

class WorkerShutdownTest {
  @Test void cancellationCannotClaimDrainForANoncooperativePhysicalOwner() throws Exception {
    var fixture = new CoreFixture();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var exited = new CountDownLatch(1);
    fixture.behavior.set(request -> {
      entered.countDown();
      boolean done = false;
      try {
        while (!done) {
          try { done = release.await(2, TimeUnit.SECONDS); }
          catch (InterruptedException deliberatelyIgnored) { /* synthetic noncooperative transport */ }
        }
        return FetchResult.found("late", null);
      } finally { exited.countDown(); }
    });
    fixture.enqueue();
    var persistence = Schedulers.newBoundedElastic(1, 8, "shutdown-jdbc");
    var owner = Schedulers.newBoundedElastic(1, 8, "shutdown-owner");
    var worker = new ReactorSyncWorker(fixture.coordinator, persistence, owner, 1,
        Duration.ofMillis(10), Duration.ofMillis(5), Duration.ofMillis(40), Duration.ofMillis(100));
    try {
      worker.start(); assertTrue(entered.await(2, TimeUnit.SECONDS));
      worker.stop();
      assertFalse(worker.isRunning());
      assertEquals(WorkerShutdownStatus.TIMED_OUT, worker.shutdown().status());
      assertEquals(1, worker.shutdown().physicalOperations());
      assertEquals(1, worker.shutdown().ownerOperations());
      assertTrue(worker.shutdown().elapsedMillis() < 600);
      assertEquals(0, worker.activity().inFlight(), "logical cancellation can reach zero before physical exit");
      assertThrows(IllegalStateException.class, worker::start);
      release.countDown(); assertTrue(exited.await(2, TimeUnit.SECONDS));
      worker.stop(); assertEquals(WorkerShutdownStatus.DRAINED, worker.shutdown().status());
      assertTrue(fixture.projection.load(fixture.key.subject()).isEmpty(), "a cancelled late result cannot apply");
    } finally { release.countDown(); worker.stop(); persistence.dispose(); owner.dispose(); }
  }

  @Test void gracefulStopFinishesAdmittedWorkAndClosesFurtherAdmission() throws Exception {
    var fixture = new CoreFixture(); fixture.enqueue();
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    fixture.behavior.set(request -> {
      entered.countDown(); assertTrue(release.await(2, TimeUnit.SECONDS));
      return FetchResult.found("drained", null);
    });
    var persistence = Schedulers.newBoundedElastic(1, 8, "drain-jdbc");
    var owner = Schedulers.newBoundedElastic(1, 8, "drain-owner");
    var worker = new ReactorSyncWorker(fixture.coordinator, persistence, owner, 1, Duration.ofMillis(10));
    var stopped = new CountDownLatch(1);
    try {
      worker.start(); assertTrue(entered.await(2, TimeUnit.SECONDS));
      var stopper = new Thread(() -> worker.stop(stopped::countDown), "owned-stop-test");
      stopper.start();
      long bound = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (worker.isRunning() && System.nanoTime() < bound) Thread.onSpinWait();
      assertFalse(worker.isRunning()); release.countDown();
      assertTrue(stopped.await(2, TimeUnit.SECONDS)); stopper.join(2000);
      assertEquals(WorkerShutdownStatus.DRAINED, worker.shutdown().status());
      assertEquals(0, worker.shutdown().physicalOperations());
      assertEquals("drained", fixture.local().value());
      assertEquals(1, fixture.calls.get());
      assertFalse(worker.tick().block(Duration.ofSeconds(1)));
    } finally { release.countDown(); worker.stop(); persistence.dispose(); owner.dispose(); }
  }
}
