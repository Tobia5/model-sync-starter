package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.*;
import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.WorkStore;
import io.modelsync.spring.SpringLocalTransactions;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Controlled four-way diagnostic: legacy, selector only, admission only, combined, then reverse. */
class OracleOptimizationIT {
  private static final int ROWS=100_000;
  private static final List<String> FORWARD=List.of("baseline","selector-only","admission-only","combined");
  private static final Path REPORT=Path.of("target/qm/oracle-optimization.json");

  @Test void comparesIndependentChangesOnTheSameOraclePopulation() throws Exception {
    assertEquals("DEDICATED_TEST_SCHEMA",System.getenv("MODELSYNC_ORACLE_TEST_SCHEMA_ACK"));
    String user=System.getenv("MODELSYNC_ORACLE_USER");
    assertNotNull(user);assertTrue(user.matches("MS_TEST_[A-Za-z0-9_]{1,20}"));
    var config=new HikariConfig();
    config.setJdbcUrl(System.getenv("MODELSYNC_ORACLE_URL"));config.setUsername(user);
    config.setPassword(System.getenv("MODELSYNC_ORACLE_PASSWORD"));
    config.setDriverClassName("oracle.jdbc.OracleDriver");config.setMaximumPoolSize(8);config.setMinimumIdle(8);
    config.setMaxLifetime(0);config.setConnectionInitSql("alter session set statistics_level=all");
    config.setPoolName("modelsync-comparison");
    try(var source=new HikariDataSource(config)) {
      var jdbc=new SelectorJdbcTemplate(source);
      assertEquals(0,jdbc.queryForObject("select count(*) from MS_WORK",Integer.class));
      assertEquals(0,jdbc.queryForObject("select count(*) from user_tables where table_name='MS_TEST_PROJECTION'",Integer.class));
      jdbc.execute("create table MS_TEST_PROJECTION (tenant_id varchar2(128 char) not null,subject_id varchar2(256 char) not null,projection_value varchar2(512 char) not null,revision varchar2(36 char) not null,expires_at_ms number(19),primary key(tenant_id,subject_id))");
      String binding="comparison-"+UUID.randomUUID();
      var report=new LinkedHashMap<String,Object>();
      report.put("scope","controlled-oracle-queue-comparison-not-production-capacity");
      report.put("rows",ROWS);report.put("due_rows",ROWS);report.put("workers",4);report.put("pool",8);
      report.put("owner_spacing_ms",20);report.put("owner_latency_ms",List.of(5,25));
      report.put("provider_429_every",17);report.put("timeout_every",29);report.put("owner_call_target",230);
      report.put("statistics","ALL; gathered no-histogram table/index stats before each round");
      report.put("cache_control","Same physical table/index catalog; no shared/global cache flush; 10 owner-entry warmup per variant; forward/reverse order; touched rows reset between variants");
      List<Map<String,Object>> results=new ArrayList<>();report.put("results",results);
      try {
        new JdbcWorkStore(jdbc).verifySchema();
        report.put("empty_queue_results", emptyQueue(jdbc,binding));
        OracleMeasurements.write(REPORT,report);
        var seedBefore=OracleMeasurements.snapshot(jdbc);long seedStart=System.nanoTime();
        var seedTx=new TransactionTemplate(new DataSourceTransactionManager(source));
        List<Object[]> batch=new ArrayList<>(1000);
        for(int i=1;i<=ROWS;i++) {
          var key=new WorkKey(binding,new SubjectKey("synthetic",Integer.toString(i)));
          batch.add(new Object[]{key.storageId(),binding,"synthetic",key.subject().id(),Priority.NORMAL.ordinal()});
          if(batch.size()==1000) {
            seedTx.executeWithoutResult(s->jdbc.batchUpdate("insert into MS_WORK(work_id,binding_id,tenant_id,subject_id,priority,generation,attempts,force_refresh,work_status,next_at_ms,requested_at_ms) values(?,?,?,?,?,1,0,0,'WAITING',0,0)",batch));
            batch.clear();
          }
        }
        report.put("seed_ms",(System.nanoTime()-seedStart)/1_000_000);
        report.put("seed_work",OracleMeasurements.difference(seedBefore,OracleMeasurements.snapshot(jdbc)));
        for(int round=0;round<2;round++) {
          List<String> order=new ArrayList<>(FORWARD);if(round==1)Collections.reverse(order);
          for(String variant:order) {
            long setupStarted=System.nanoTime();var setupBefore=OracleMeasurements.snapshot(jdbc);
            jdbc.update("delete from MS_STATE where work_id in(select work_id from MS_WORK where binding_id=?)",binding);
            jdbc.update("delete from MS_TEST_PROJECTION");
            long requested=System.currentTimeMillis();
            jdbc.update("update MS_WORK set priority=?,generation=1,attempts=0,force_refresh=0,work_status='WAITING',next_at_ms=0,requested_at_ms=?,lease_token=null,lease_until_ms=null,leased_generation=null,last_error_code=null where binding_id=? and (work_status<>'WAITING' or next_at_ms<>0 or priority<>? or attempts<>0)",Priority.NORMAL.ordinal(),requested,binding,Priority.NORMAL.ordinal());
            // Equal lag origin for all rows, outside processing counters; no source refresh/retention reset.
            jdbc.update("update MS_WORK set requested_at_ms=? where binding_id=?",requested,binding);
            jdbc.execute("begin dbms_stats.gather_table_stats(ownname=>user,tabname=>'MS_WORK',cascade=>true,estimate_percent=>dbms_stats.auto_sample_size,method_opt=>'FOR ALL COLUMNS SIZE 1',no_invalidate=>false);end;");
            var result=new LinkedHashMap<String,Object>();result.put("variant",variant);result.put("round",round);
            result.put("setup_ms",(System.nanoTime()-setupStarted)/1_000_000);
            result.put("setup_work",OracleMeasurements.difference(setupBefore,OracleMeasurements.snapshot(jdbc)));
            result.put("index_statistics",jdbc.queryForList("select index_name,num_rows,leaf_blocks,clustering_factor from user_indexes where table_name='MS_WORK' order by index_name"));
            result.put("nls_session",jdbc.queryForList("select parameter,value from nls_session_parameters where parameter in ('NLS_LANGUAGE','NLS_SORT','NLS_COMP') order by parameter"));
            result.put("table_statistics",jdbc.queryForList("select num_rows,blocks,to_char(last_analyzed,'YYYY-MM-DD\"T\"HH24:MI:SS') last_analyzed_db_local from user_tab_statistics where table_name='MS_WORK'"));
            results.add(result);OracleMeasurements.write(REPORT,report);
            measure(jdbc,binding,variant,result);
            OracleMeasurements.write(REPORT,report);
          }
        }
        assertEquals(8,results.size());
        for(int round=0;round<2;round++) {
          int current=round;
          var baseline=results.stream().filter(r->r.get("variant").equals("baseline")&&(int)r.get("round")==current).findFirst().orElseThrow();
          var selectorOnly=results.stream().filter(r->r.get("variant").equals("selector-only")&&(int)r.get("round")==current).findFirst().orElseThrow();
          var combined=results.stream().filter(r->r.get("variant").equals("combined")&&(int)r.get("round")==current).findFirst().orElseThrow();
          assertTrue((double)combined.get("logical_gets_per_completed")<(double)baseline.get("logical_gets_per_completed"),"Combined must reduce work per real completed reconciliation");
          assertTrue((long)combined.get("quota_denials")<=(long)baseline.get("quota_denials"),"Combined must not increase baseline denials");
          long selectorDenials=(long)selectorOnly.get("quota_denials");
          assertTrue(selectorDenials==0 ? (long)combined.get("quota_denials")==0
              : (long)combined.get("quota_denials")<selectorDenials,"Admission scheduling must reduce selector-only denial churn, preserving zero when already zero");
        }
        report.put("status","PASS");
      } finally {
        var before=OracleMeasurements.snapshot(jdbc);long queriesBefore=jdbc.selectorQueries.get();long start=System.nanoTime();
        jdbc.update("delete from MS_STATE where work_id in(select work_id from MS_WORK where binding_id=?)",binding);
        jdbc.update("delete from MS_WORK where binding_id=?",binding);
        jdbc.execute("drop table MS_TEST_PROJECTION purge");
        report.put("cleanup_ms",(System.nanoTime()-start)/1_000_000);
        report.put("cleanup_work",OracleMeasurements.difference(before,OracleMeasurements.snapshot(jdbc)));
        report.put("cleanup_rows_remaining",jdbc.queryForObject("select count(*) from MS_WORK where binding_id=?",Integer.class,binding));
        OracleMeasurements.write(REPORT,report);
      }
    }
  }

  private static List<Map<String,Object>> emptyQueue(SelectorJdbcTemplate jdbc,String binding) {
    List<Map<String,Object>> results=new ArrayList<>();
    var manager=new DataSourceTransactionManager(jdbc.getDataSource());
    for(String variant:FORWARD) {
      String group="empty-"+UUID.randomUUID();
      var gate=new JdbcQuotaGate(jdbc,manager,Clock.systemUTC());gate.register(group,Duration.ofMillis(20));
      var quota=new MeasuredQuotaGate(gate,variant.equals("admission-only")||variant.equals("combined"));
      WorkStore selected=new JdbcWorkStore(jdbc);
      if(variant.equals("baseline")||variant.equals("admission-only"))selected=new LegacyOracleWorkStore(jdbc,selected);
      var counted=new CountedWorkStore(selected);
      var model=ModelBinding.builder(binding,String.class).boundary("oracle-comparison").owner("must-not-call",r->{throw new AssertionError("Empty queue called owner");})
          .replica(new OracleTestProjectionStore(jdbc)).fingerprint(Digests::sha256Fields).quota(group).build();
      var c=new SyncCoordinator(new ModelRegistry(List.of(model)),counted,new SpringLocalTransactions(manager),quota,Clock.systemUTC(),SyncOptions.defaults(),WeightedPriorities.defaults(),()->.5);
      try {
        var before=OracleMeasurements.snapshot(jdbc);long queriesBefore=jdbc.selectorQueries.get();long start=System.nanoTime();
        for(int i=0;i<20;i++)assertFalse(c.runOne());
        var result=new LinkedHashMap<String,Object>();result.put("variant",variant);result.put("polls",20);
        result.put("selector_queries",jdbc.selectorQueries.get()-queriesBefore);
        result.put("empty_polls",20);result.put("selection_attempts",counted.selections.get());
        result.put("quota_grants",quota.granted.get());result.put("quota_denials",quota.denied.get());result.put("owner_calls",0);
        result.put("elapsed_ms",(System.nanoTime()-start)/1_000_000);
        result.put("work",OracleMeasurements.difference(before,OracleMeasurements.snapshot(jdbc)));results.add(result);
        assertEquals(0,quota.granted.get());assertEquals(0,quota.denied.get());
        assertEquals(0L,jdbc.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group=?",Long.class,group));
      } finally {jdbc.update("delete from MS_QUOTA where quota_group=?",group);}
    }
    return results;
  }

  private static void measure(SelectorJdbcTemplate jdbc,String binding,String variant,Map<String,Object> result) throws Exception {
    String group="comparison-"+UUID.randomUUID();
    var manager=new DataSourceTransactionManager(jdbc.getDataSource());
    var tx=new SpringLocalTransactions(manager);
    WorkStore selected=new JdbcWorkStore(jdbc);
    if(variant.equals("baseline")||variant.equals("admission-only"))selected=new LegacyOracleWorkStore(jdbc,selected);
    var store=new CountedWorkStore(selected);
    var gate=new JdbcQuotaGate(jdbc,manager,Clock.systemUTC());gate.register(group,Duration.ofMillis(20));
    var quota=new MeasuredQuotaGate(gate,variant.equals("admission-only")||variant.equals("combined"));
    var calls=new AtomicInteger();var limited=new AtomicInteger();var timeouts=new AtomicInteger();
    try {
      var model=ModelBinding.builder(binding,String.class).boundary("oracle-comparison").owner("controlled-local-owner",request->{
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        int call=calls.incrementAndGet();Thread.sleep(call%3==0?25:5);
        if(call%29==0){timeouts.incrementAndGet();throw new SocketTimeoutException("synthetic timeout");}
        if(call%17==0){limited.incrementAndGet();throw new RateLimitedAfter(Duration.ofMillis(100));}
        return FetchResult.found("source-"+request.key().id(),"v1");
      }).replica(new OracleTestProjectionStore(jdbc)).repairFromOwner().fingerprint(Digests::sha256Fields)
          .quota(group).freshness(new FreshnessPolicy(Duration.ofMinutes(5),Duration.ofMinutes(10),Duration.ofHours(1))).build();
      var c=new SyncCoordinator(new ModelRegistry(List.of(model)),store,tx,quota,Clock.systemUTC(),SyncOptions.defaults(),WeightedPriorities.defaults(),()->.5);
      long warmupStarted=System.nanoTime();
      while(calls.get()<10) {assertTrue(System.nanoTime()-warmupStarted<TimeUnit.SECONDS.toNanos(30));c.runOne();Thread.sleep(5);}
      result.put("warmup_ms",(System.nanoTime()-warmupStarted)/1_000_000);
      long warmCalls=calls.get();long warmCompleted=completed(jdbc,binding);
      long grants=quota.granted.get(),denied=quota.denied.get(),selections=store.selections.get(),empty=store.empty.get(),queriesBefore=jdbc.selectorQueries.get();
      var before=OracleMeasurements.snapshot(jdbc);
      var polls=new AtomicLong();var attempts=new AtomicLong();var emptyPolls=new AtomicLong();
      List<Long> claimedLatency=Collections.synchronizedList(new ArrayList<>());
      List<Long> emptyLatency=Collections.synchronizedList(new ArrayList<>());
      long started=System.nanoTime();long deadline=started+TimeUnit.MINUTES.toNanos(3);
      try(var workers=Executors.newFixedThreadPool(4)) {
        List<Future<?>> futures=new ArrayList<>();
        for(int i=0;i<4;i++)futures.add(workers.submit(()->{
          while(calls.get()<230&&System.nanoTime()<deadline) {
            long callStart=System.nanoTime();boolean claimed=c.runOne();polls.incrementAndGet();
            long latency=(System.nanoTime()-callStart)/1_000_000;
            if(claimed){attempts.incrementAndGet();claimedLatency.add(latency);}
            else {emptyPolls.incrementAndGet();emptyLatency.add(latency);}
            Thread.sleep(5);
          }
          return null;
        }));
        for(var f:futures)f.get(200,TimeUnit.SECONDS);
      }
      long elapsed=(System.nanoTime()-started)/1_000_000;
      var after=OracleMeasurements.snapshot(jdbc);
      var counters=OracleMeasurements.delta(OracleMeasurements.statisticsFrom(before),OracleMeasurements.statisticsFrom(after));
      long completed=completed(jdbc,binding), measuredCompleted=completed-warmCompleted;
      long measuredCalls=calls.get()-warmCalls,measuredGrants=quota.granted.get()-grants,measuredDenials=quota.denied.get()-denied;
      result.put("selector_queries",jdbc.selectorQueries.get()-queriesBefore);
      result.put("measurement_ms",elapsed);result.put("polls",polls.get());result.put("empty_polls",emptyPolls.get());
      result.put("ledger_attempts",attempts.get());result.put("selection_attempts",store.selections.get()-selections);result.put("empty_selections",store.empty.get()-empty);
      result.put("quota_grants",measuredGrants);result.put("quota_denials",measuredDenials);
      result.put("measured_owner_calls",measuredCalls);result.put("total_owner_calls",calls.get());
      result.put("measured_completed_syncs",measuredCompleted);result.put("total_completed_syncs",completed);
      result.put("completed_syncs_per_second",1000.0*measuredCompleted/Math.max(1,elapsed));
      result.put("logical_gets_per_completed",(counters.get("consistent gets")+counters.get("db block gets"))/(double)measuredCompleted);
      result.put("claimed_run_one_latency_ms",percentiles(claimedLatency));result.put("empty_poll_latency_ms",percentiles(emptyLatency));
      result.put("measurement_work",OracleMeasurements.difference(before,after));
      result.put("claim_plans",OracleMeasurements.claimPlans(jdbc,OracleMeasurements.sqlFrom(before)));
      result.put("lag_ms",percentiles(jdbc.query("select s.reconciled_at_ms-w.requested_at_ms from MS_STATE s join MS_WORK w on w.work_id=s.work_id where w.binding_id=? and s.outcome in ('MATCH','APPLIED')",(r,n)->r.getLong(1),binding)));
      result.put("provider_429",limited.get());result.put("timeouts",timeouts.get());
      // Always write partial measurements before assertions; the caller's finally retains them.
      assertTrue(calls.get()>=230);assertTrue(completed>=150);assertTrue(measuredCompleted>0);
      assertEquals(completed,jdbc.queryForObject("select count(*) from MS_TEST_PROJECTION",Long.class));
      assertEquals(measuredCalls,measuredGrants);assertEquals(attempts.get(),measuredGrants+measuredDenials);
      assertEquals(polls.get(),attempts.get()+emptyPolls.get());
      assertTrue(limited.get()>0&&timeouts.get()>0);
      result.put("status","PASS");
    } finally {jdbc.update("delete from MS_QUOTA where quota_group=?",group);}
  }

  private static long completed(JdbcTemplate jdbc,String binding) {
    return jdbc.queryForObject("select count(*) from MS_STATE s join MS_WORK w on w.work_id=s.work_id where w.binding_id=? and s.outcome in ('MATCH','APPLIED')",Long.class,binding);
  }
  private static Map<String,Long> percentiles(List<Long> values) {
    if(values.isEmpty())return Map.of();
    var sorted=new ArrayList<>(values);Collections.sort(sorted);
    var result=new LinkedHashMap<String,Long>();
    for(int p:List.of(50,95,99))result.put("p"+p,sorted.get(Math.min(sorted.size()-1,(int)Math.ceil(sorted.size()*p/100.0)-1)));
    return result;
  }
}
