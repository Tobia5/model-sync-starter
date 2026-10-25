package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;
import com.zaxxer.hikari.HikariDataSource;
import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.jdbc.*;
import io.modelsync.reactor.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;

/** Actual starter Reactor/JPA/JDBC host, not comparison runOne lanes or an in-memory durability proof. */
class OracleCooldownWorkerIT {
  @Test void pendingPublicationDoesNotLoseLanesAndIndependentProviderCompletes()throws Exception {
    var report=FaultEvidence.report("actual-worker-durable-cooldown");report.put("status","FAILED");
    report.put("workers",4);report.put("runtime_pool",8);report.put("observation_ms",5000);
    var entered=new CountDownLatch(1);var outcome=new CountDownLatch(1);var latch=new CooldownWorkerLatch();
    var executor=Executors.newSingleThreadExecutor();Future<?> held=null;
    try(var competitor=RecoveryConfiguration.source(System.getenv("MODELSYNC_ORACLE_URL"),2,10000);
        var context=new SpringApplicationBuilder(WorkerRuntimeConfiguration.class).web(WebApplicationType.NONE).profiles("worker-oracle")
        .run("--spring.jpa.hibernate.ddl-auto=validate","--spring.jpa.open-in-view=false","--spring.main.banner-mode=off","--logging.level.root=WARN",
            "--model-sync.enabled=true","--model-sync.worker-enabled=true","--model-sync.aop.enabled=false",
            "--model-sync.jdbc.point-lock-policy=ORACLE_NOWAIT","--model-sync.jdbc.quota-lock-policy=ORACLE_NOWAIT","--model-sync.jdbc.insert-policy=ORACLE_26_NOWAIT",
            "--model-sync.jdbc.cooldown-policy=DURABLE","--model-sync.jpa.lock-policy=ORACLE_NOWAIT",
            "--model-sync.quotas.worker-main=20ms","--model-sync.quotas.worker-other=1ms")) {
      var worker=context.getBean(ReactorSyncWorker.class);worker.stop();
      var source=context.getBean(HikariDataSource.class);var jdbc=context.getBean(WorkerJdbcTemplate.class);var view=new JdbcTemplate(competitor);
      var owner=context.getBean(WorkerOwner.class);var quota=context.getBean(WorkerQuota.class);var gateway=context.getBean(SyncGateway.class);
      QualificationTargetAssertions.host(context,"cooldown-worker");assertSame(source,context.getBean(JpaTransactionManager.class).getDataSource());
      owner.behavior=request->{if(request.key().id().equals("limited")){entered.countDown();assertTrue(outcome.await(5,TimeUnit.SECONDS));throw new RateLimitedAfter(Duration.ofSeconds(30));}
        return FetchResult.found(new Customer("synthetic","ACTIVE"),"cooldown-worker-v1");};
      try {
        gateway.request("worker-main","synthetic-cooldown","limited",Priority.URGENT,true);worker.start();assertTrue(entered.await(5,TimeUnit.SECONDS));
        var manager=new DataSourceTransactionManager(competitor);manager.setTransactionExecutionListeners(List.of(latch));
        var other=new JdbcQuotaGate(view,manager,Clock.systemUTC(),JdbcQuotaLockPolicy.ORACLE_NOWAIT,JdbcInsertPolicy.ORACLE_26_NOWAIT,JdbcCooldownPolicy.DURABLE);
        await(()->System.currentTimeMillis()>=view.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group='worker-main'",Long.class),1000);
        held=executor.submit(()->assertTrue(other.acquire("worker-main").granted()));assertTrue(latch.held.await(5,TimeUnit.SECONDS));
        outcome.countDown();
        await(()->view.queryForObject("select count(*) from MS_COOLDOWN where quota_group='worker-main' and publication_status='PENDING'",Integer.class)==1,5000);
        await(()->view.queryForObject("select count(*) from MS_WORK where binding_id='worker-main' and last_error_code='COOLDOWN_PENDING'",Integer.class)==1,5000);
        assertFalse(held.isDone());assertTrue(worker.isRunning());
        gateway.request("worker-other","synthetic-cooldown","independent",Priority.URGENT,true);
        await(()->view.queryForObject("select count(*) from MS_STATE s join MS_WORK w on s.work_id=w.work_id where w.binding_id='worker-other' and s.outcome='APPLIED'",Integer.class)==1,5000);
        assertEquals(2,owner.calls.get());assertEquals(2,quota.grants.get());assertEquals(1,worker.activity().failed(),"Existing RateLimitedAfter tick classification remains FAILED; publication itself did not escape");
        report.put("scheduling",WorkerMeasurements.scheduling(worker,jdbc,owner,quota));report.put("worker_still_running",worker.isRunning());
        worker.stop();assertEquals(WorkerShutdownStatus.DRAINED,worker.shutdown().status());assertEquals(0,worker.physicalOperations());
        assertEquals(0,owner.active.get());assertEquals(0,source.getHikariPoolMXBean().getActiveConnections());assertFalse(held.isDone());
        report.put("blocker_released_for_pending_proof",false);report.put("shutdown",worker.shutdown());
        report.put("runtime_pool_active_after_stop",source.getHikariPoolMXBean().getActiveConnections());
        report.put("physical_owner_calls",owner.calls.get());report.put("local_committed_admissions",quota.grants.get());
        long deadline=view.queryForObject("select deadline_ms from MS_COOLDOWN where quota_group='worker-main'",Long.class);
        latch.release.countDown();held.get(5,TimeUnit.SECONDS);manager.setTransactionExecutionListeners(List.of());
        var restarted=new JdbcQuotaGate(view,manager,Clock.systemUTC(),JdbcQuotaLockPolicy.ORACLE_NOWAIT,JdbcInsertPolicy.ORACLE_26_NOWAIT,JdbcCooldownPolicy.DURABLE);
        assertEquals(1,restarted.recoverCooldowns("worker-main",1));assertFalse(restarted.acquire("worker-main").granted());
        assertEquals(deadline,view.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group='worker-main'",Long.class));
        assertEquals(2,owner.calls.get());assertEquals(0,competitor.getHikariPoolMXBean().getActiveConnections());
        report.put("recovery_without_extra_owner_read",true);report.put("status","PASS");
      } finally {
        outcome.countDown();latch.release.countDown();worker.stop();
        if(worker.shutdown().status()==WorkerShutdownStatus.DRAINED) {
          jdbc.update("delete from MS_STATE where work_id in(select work_id from MS_WORK where binding_id in ('worker-main','worker-other'))");
          jdbc.update("delete from MS_WORK where binding_id in ('worker-main','worker-other')");
          jdbc.update("delete from MS_COOLDOWN where quota_group in ('worker-main','worker-other')");
          jdbc.update("delete from MS_QUOTA where quota_group in ('worker-main','worker-other')");jdbc.execute("drop table SAMPLE_CUSTOMER purge");
          report.put("owned_fixture_cleanup",true);
        }
      }
    } finally {
      outcome.countDown();latch.release.countDown();if(held!=null)held.get(5,TimeUnit.SECONDS);
      executor.shutdownNow();report.put("owned_executor_terminated",executor.awaitTermination(5,TimeUnit.SECONDS));FaultEvidence.write("oracle-cooldown-worker.json",report);
    }
  }
  private static void await(BooleanSupplier condition,long ms)throws Exception {
    long bound=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(ms);while(!condition.getAsBoolean()){if(System.nanoTime()>=bound)fail("Owned worker observation bound");Thread.sleep(10);}
  }
}
