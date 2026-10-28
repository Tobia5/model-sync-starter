package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.*;
import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.*;
import io.modelsync.spring.SpringLocalTransactions;
import java.nio.file.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/** One-row proof of newly observed acquisition/refusal and publication counters. Never a scale fixture. */
final class OracleAcquisitionProbe {
  private static final Path REPORT=Path.of("target/qm/oracle-acquisition-probe.json");
  private OracleAcquisitionProbe() {}
  static void run() throws Exception {
    assertEquals("DEDICATED_TEST_SCHEMA",System.getenv("MODELSYNC_ORACLE_TEST_SCHEMA_ACK"));
    String user=System.getenv("MODELSYNC_ORACLE_USER");assertNotNull(user);assertTrue(user.matches("MS_TEST_[A-Za-z0-9_]{1,20}"));
    Path root=Path.of(System.getProperty("modelsync.repository.root","..")).toAbsolutePath().normalize();
    @SuppressWarnings("unchecked") var report=new LinkedHashMap<String,Object>(JsonMapper.builder().build().readValue(
        Files.readString(root.resolve("target/qm/run-context.json")),Map.class));
    report.put("scope","one-row-acquisition-accounting-not-capacity");report.put("status","FAILED");report.put("observation_ms",5000);
    report.put("quota_policy","ORACLE_NOWAIT");report.put("insert_policy","ORACLE_26_NOWAIT");
    String binding="account-probe-"+UUID.randomUUID(), group="quota-probe-"+UUID.randomUUID(), guard="guard-"+UUID.randomUUID();
    var key=new WorkKey(binding,new SubjectKey("synthetic","one"));
    boolean projectionOwned=false;
    var executor=Executors.newSingleThreadExecutor(r->new Thread(r,"modelsync-accounting-owned"));
    try(var source=source("accounting-runtime",2);var observer=source("accounting-observer",2)) {
      var jdbc=new JdbcTemplate(source);var view=new JdbcTemplate(observer);jdbc.setQueryTimeout(2);view.setQueryTimeout(2);
      assertEquals(0,view.queryForObject("select count(*) from MS_WORK",Integer.class));
      assertEquals(0,view.queryForObject("select count(*) from user_tables where table_name='MS_TEST_PROJECTION'",Integer.class));
      jdbc.execute("create table MS_TEST_PROJECTION(tenant_id varchar2(128 char) not null,subject_id varchar2(256 char) not null,projection_value varchar2(512 char) not null,revision varchar2(36 char) not null,expires_at_ms number(19),primary key(tenant_id,subject_id))");projectionOwned=true;
      try {
        try(var c=source.getConnection()) {report.put("oracle",c.getMetaData().getDatabaseProductVersion());report.put("driver",c.getMetaData().getDriverVersion());}
        var manager=new DataSourceTransactionManager(source);var baseTx=new SpringLocalTransactions(manager);
        var baseStore=OracleQualificationStores.work(jdbc);
        var nativeQuota=OracleQualificationStores.quota(jdbc,manager,Clock.systemUTC());nativeQuota.register(group,Duration.ofMillis(20));nativeQuota.register(guard,Duration.ofMillis(1));
        var probe=new AcquisitionTransactionProbe(jdbc,view,guard);manager.setTransactionExecutionListeners(List.of(probe));
        var accounting=new ComparisonAccounting();var tx=new ObservedLocalTransactions(baseTx,accounting);
        var store=new ObservedWorkStore(baseStore,tx);var quota=new MeasuredQuotaGate(nativeQuota,false,accounting);
        var physical=new AtomicInteger();
        var model=ModelBinding.builder(binding,String.class).boundary("synthetic-accounting-probe")
            .owner("local",new ObservedOwner<String>(request->{physical.incrementAndGet();return FetchResult.found("source","v1");},accounting))
            .replica(new OracleTestProjectionStore(jdbc)).repairFromOwner().fingerprint(Digests::sha256Fields).quota(group).build();
        var coordinator=new SyncCoordinator(new ModelRegistry(List.of(model)),store,tx,quota,Clock.systemUTC(),SyncOptions.defaults(),WeightedPriorities.defaults(),()->.5);
        baseTx.required(()->baseStore.enqueue(key,Priority.NORMAL,true,Instant.now()));
        var cases=new ArrayList<Map<String,Object>>();report.put("cases",cases);OracleMeasurements.write(REPORT,report);
        var refused=new LinkedHashMap<String,Object>();refused.put("name","held-acquisition");refused.put("blocker_released_for_proof",false);cases.add(refused);
        accounting.begin("held-acquire",quota);probe.armed=true;
        try(var blocker=block(view,group)) {
          long start=System.nanoTime();var future=executor.submit(()->accounting.runOne(coordinator));
          assertTrue(future.get(5,TimeUnit.SECONDS));refused.put("client_return_ms",(System.nanoTime()-start)/1_000_000);
          var facts=accounting.finish(quota);refused.put("accounting",facts);ComparisonAccountingValidator.check(facts,0,0);
          assertEquals(1,quota.acquisitions.get());assertEquals(1,quota.failures.get());assertEquals(1,quota.refusals.get());
          assertEquals(0,quota.granted.get());assertEquals(0,quota.denied.get());assertEquals(0,physical.get());
          assertEquals(0L,view.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group=?",Long.class,group));
          verifyRollback(probe,view,guard,refused);
          var durable=view.queryForMap("select work_status,last_error_code,attempts from MS_WORK where work_id=?",key.storageId());
          assertEquals("WAITING",durable.get("WORK_STATUS"));assertEquals("LOCAL_CONTENTION",durable.get("LAST_ERROR_CODE"));
          assertEquals(1,((Number)durable.get("ATTEMPTS")).intValue());refused.put("durable_intent",durable);
          assertEquals(0,source.getHikariPoolMXBean().getActiveConnections());assertEquals(1,view.queryForObject("select 1 from dual",Integer.class));
          refused.put("status","PASS");OracleMeasurements.write(REPORT,report);
        } finally {probe.armed=false;}
        baseTx.required(()->baseStore.enqueue(key,Priority.NORMAL,true,Instant.now()));
        accounting.begin("recovery",quota);assertTrue(accounting.runOne(coordinator));var recovered=accounting.finish(quota);
        assertEquals(1,physical.get());assertEquals(1,view.queryForObject("select count(*) from MS_STATE where work_id=? and outcome in ('MATCH','APPLIED')",Integer.class,key.storageId()));
        ComparisonAccountingValidator.check(recovered,1,1);report.put("recovery_accounting",recovered);
        long prior=System.currentTimeMillis()+30_000;nativeQuota.cooldown(group,Instant.ofEpochMilli(prior));
        var publications=new MeasuredQuotaGate(nativeQuota);probe.transactions.clear();probe.armed=true;
        var publication=new LinkedHashMap<String,Object>();publication.put("name","held-cooldown");publication.put("blocker_released_for_proof",false);cases.add(publication);
        try(var blocker=block(view,group)) {
          long start=System.nanoTime();var future=executor.submit(()->assertThrows(LocalContention.class,()->publications.cooldown(group,Instant.ofEpochMilli(prior+30_000))));
          var failure=future.get(5,TimeUnit.SECONDS);publication.put("client_return_ms",(System.nanoTime()-start)/1_000_000);
          publication.put("failure",DetachedFailure.capture(failure).facts());publication.put("counters",publications.counters());
          assertEquals(1,publications.cooldowns.get());assertEquals(0,publications.published.get());assertEquals(1,publications.cooldownFailures.get());
          assertEquals(prior,view.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group=?",Long.class,group));
          verifyRollback(probe,view,guard,publication);publication.put("extension_persisted",false);publication.put("status","PASS");
          OracleMeasurements.write(REPORT,report);
        } finally {probe.armed=false;}
        publications.cooldown(group,Instant.ofEpochMilli(prior+30_000));publications.cooldown(group,Instant.ofEpochMilli(prior));
        assertEquals(prior+30_000,view.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group=?",Long.class,group));
        assertEquals(3,publications.cooldowns.get());assertEquals(2,publications.published.get());assertEquals(1,publications.cooldownFailures.get());
        report.put("cooldown_recovery_counters",publications.counters());report.put("failed_extension_was_not_published",true);
        report.put("pool_active_after",source.getHikariPoolMXBean().getActiveConnections());assertEquals(0,source.getHikariPoolMXBean().getActiveConnections());
        report.put("status","PASS");
      } finally {
        jdbc.update("delete from MS_STATE where work_id=?",key.storageId());jdbc.update("delete from MS_WORK where work_id=?",key.storageId());
        jdbc.update("delete from MS_QUOTA where quota_group in (?,?)",group,guard);
        if(projectionOwned)jdbc.execute("drop table MS_TEST_PROJECTION purge");
        report.put("owned_fixture_cleanup",true);OracleMeasurements.write(REPORT,report);
      }
    } finally {executor.shutdownNow();report.put("owned_executor_terminated",executor.awaitTermination(5,TimeUnit.SECONDS));OracleMeasurements.write(REPORT,report);}
  }
  private static HikariDataSource source(String name,int size) {
    var config=new HikariConfig();config.setJdbcUrl(System.getenv("MODELSYNC_ORACLE_URL"));config.setUsername(System.getenv("MODELSYNC_ORACLE_USER"));config.setPassword(System.getenv("MODELSYNC_ORACLE_PASSWORD"));
    config.setDriverClassName("oracle.jdbc.OracleDriver");config.setMaximumPoolSize(size);config.setMinimumIdle(size);config.setConnectionTimeout(1000);
    config.addDataSourceProperty("oracle.net.CONNECT_TIMEOUT","1500");config.addDataSourceProperty("oracle.jdbc.ReadTimeout","10000");config.setPoolName(name);
    return new HikariDataSource(config);
  }
  private static Connection block(JdbcTemplate view,String group)throws Exception {
    var c=view.getDataSource().getConnection();c.setAutoCommit(false);
    try(var s=c.prepareStatement("select quota_group from MS_QUOTA where quota_group=? for update")) {s.setString(1,group);try(var rows=s.executeQuery()){assertTrue(rows.next());}}
    return c;
  }
  private static void verifyRollback(AcquisitionTransactionProbe probe,JdbcTemplate view,String guard,Map<String,Object> result) {
    assertEquals(1,probe.transactions.size());var tx=probe.transactions.getFirst();result.put("transaction",tx);
    assertEquals("ROLLED_BACK",tx.get("completion"));var before=(Map<?,?>)tx.get("server_before_rollback");var after=(Map<?,?>)tx.get("server_after_rollback");
    assertEquals(0,((Number)before.get("LOCK_WAIT")).intValue());assertEquals(1,((Number)before.get("TRANSACTION_ACTIVE")).intValue());
    assertEquals(0,((Number)after.get("LOCK_WAIT")).intValue());assertEquals(0,((Number)after.get("TRANSACTION_ACTIVE")).intValue());
    assertEquals(0L,view.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group=?",Long.class,guard));
    result.put("guard_rolled_back",true);probe.transactions.clear();
  }
}
