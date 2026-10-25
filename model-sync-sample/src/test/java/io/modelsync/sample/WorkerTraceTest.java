package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;
import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.reactor.ReactorSyncWorker;
import io.modelsync.testkit.CoreFixture;
import io.modelsync.testkit.MemoryQuotaGate;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;

class WorkerTraceTest {
  @Test void actualFourLaneContextSurvivesPersistenceAndOwnerSchedulerHops() throws Exception {
    var f=new CoreFixture();var gate=new MemoryQuotaGate(Clock.systemUTC());gate.register("owner",Duration.ofMillis(1));
    var persistence=Schedulers.newBoundedElastic(4,64,"trace-jpa");var owner=Schedulers.newBoundedElastic(8,64,"trace-owner");
    try(var trace=new WorkerTrace(true)) {
      var coordinator=(SyncCoordinator)trace.coordinator(new SyncCoordinator(f.registry,f.store,f.tx,gate,Clock.systemUTC(),SyncOptions.defaults(),WeightedPriorities.defaults(),()->.5));
      var worker=new ReactorSyncWorker(coordinator,persistence,owner,4,Duration.ofMillis(25));
      for(int i=0;i<10;i++)f.gateway.request("customer","synthetic","trace"+i,Priority.URGENT,true);
      trace.begin();worker.start();
      long limit=System.nanoTime()+Duration.ofSeconds(3).toNanos();
      while(trace.claimLanes().size()<4 && System.nanoTime()<limit)Thread.sleep(10);
      worker.stop();var result=trace.end();
      @SuppressWarnings("unchecked") var events=(List<Map<String,Object>>)result.get("events");
      var lanes=new HashSet<Integer>();events.stream().filter(e->e.get("event").equals("coordinator-claim")).forEach(e->lanes.add(((Number)e.get("lane")).intValue()));
      assertEquals(Set.of(0,1,2,3),lanes);assertEquals(0L,result.get("unattributed_coordinator_calls"));assertEquals(0,result.get("dropped"));
      assertTrue(events.stream().anyMatch(e->e.get("event").equals("reservation-release")));
      assertTrue(events.stream().anyMatch(e->e.get("event").equals("scheduler-wakeup")));
      assertEquals(0,worker.physicalOperations());
    } finally {persistence.dispose();owner.dispose();}
  }
}
