package io.modelsync.reactor;

import static org.junit.jupiter.api.Assertions.*;
import io.modelsync.core.*;
import io.modelsync.core.application.SyncCoordinator;
import io.modelsync.core.domain.*;
import io.modelsync.testkit.CoreFixture;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;

class WorkerCooldownTest {
  @Test void publicationRefusalIsVisibleAndDoesNotTerminateTheActualContinuousLane()throws Exception {
    var f=new CoreFixture();var quota=new WorkerCooldownGate(f.gate,true);
    var c=new SyncCoordinator(f.registry,f.store,f.tx,quota,f.clock,SyncOptions.defaults(),WeightedPriorities.defaults(),()->.5);
    f.behavior.set(r->{if(r.key().id().equals("42"))throw new RateLimitedAfter(Duration.ofSeconds(30));return FetchResult.found("other",null);});f.enqueue();
    var p=Schedulers.newBoundedElastic(1,8,"cooldown-jdbc");var o=Schedulers.newBoundedElastic(1,8,"cooldown-owner");
    var worker=new ReactorSyncWorker(c,p,o,1,Duration.ofMillis(10),Duration.ofMillis(5),Duration.ofMillis(40),Duration.ofMillis(200));
    try {
      worker.start();long bound=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
      while(worker.activity().failed()==0&&System.nanoTime()<bound)Thread.sleep(5);
      assertEquals(1,worker.activity().failed());assertTrue(worker.isRunning());f.clock.advance(Duration.ofSeconds(1));
      f.gateway.request("customer","tenant-a","43",Priority.URGENT,true);
      var key=new SubjectKey("tenant-a","43");bound=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
      while(f.projection.load(key).isEmpty()&&System.nanoTime()<bound)Thread.sleep(5);
      assertEquals("other",f.projection.load(key).orElseThrow().value());assertEquals(2,f.calls.get());assertTrue(worker.isRunning());
      worker.stop();assertEquals(WorkerShutdownStatus.DRAINED,worker.shutdown().status());assertEquals(0,worker.physicalOperations());
    } finally {worker.stop();p.dispose();o.dispose();}
  }
}
