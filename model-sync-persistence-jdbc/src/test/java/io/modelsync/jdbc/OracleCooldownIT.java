package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;
import com.zaxxer.hikari.*;
import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.*;
import io.modelsync.spring.SpringLocalTransactions;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/** Small independent compliant transactions/processes; no selector benchmark or load fixture. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OracleCooldownIT {
  private HikariDataSource source,other,observer;
  private SelectorJdbcTemplate jdbc;
  private JdbcTemplate view;
  private DataSourceTransactionManager manager,otherManager;
  private JdbcQuotaGate gate,second;
  private AcquisitionTransactionProbe probe;
  private CooldownObservation raw;
  private String group,unrelated,guard;
  private final Clock clock=Clock.systemUTC();
  private final ExecutorService executor=Executors.newSingleThreadExecutor();
  private final List<Map<String,Object>> cases=new ArrayList<>();
  private Map<String,Object> report,result;
  private final Path path=Path.of("target/qm/oracle-cooldown.json");

  static HikariDataSource source(String name,int size) {
    if(!"DEDICATED_TEST_SCHEMA".equals(System.getenv("MODELSYNC_ORACLE_TEST_SCHEMA_ACK")))throw new IllegalStateException("Dedicated synthetic schema required");
    var c=new HikariConfig();c.setJdbcUrl(System.getenv("MODELSYNC_ORACLE_URL"));c.setUsername(System.getenv("MODELSYNC_ORACLE_USER"));c.setPassword(System.getenv("MODELSYNC_ORACLE_PASSWORD"));
    c.setDriverClassName("oracle.jdbc.OracleDriver");c.setMaximumPoolSize(size);c.setMinimumIdle(size);c.setConnectionTimeout(1000);
    c.addDataSourceProperty("oracle.net.CONNECT_TIMEOUT","1500");c.addDataSourceProperty("oracle.jdbc.ReadTimeout","10000");c.setPoolName(name);return new HikariDataSource(c);
  }
  @BeforeAll void open()throws Exception {
    var root=Path.of(System.getProperty("modelsync.repository.root","..")).toAbsolutePath().normalize();
    report=new LinkedHashMap<>(JsonMapper.builder().build().readValue(Files.readString(root.resolve("target/qm/run-context.json")),Map.class));
    report.put("scope","durable-cooldown-small-recovery");report.put("cases",cases);report.put("observation_ms",5000);
    report.put("quota_policy","ORACLE_NOWAIT");report.put("insert_policy","ORACLE_26_NOWAIT");report.put("cooldown_policy","DURABLE");
    source=source("cooldown-runtime",2);other=source("cooldown-competitor",2);observer=source("cooldown-observer",2);
    jdbc=new SelectorJdbcTemplate(source);view=new JdbcTemplate(observer);jdbc.setQueryTimeout(2);view.setQueryTimeout(2);
    try(var c=source.getConnection()){report.put("oracle",c.getMetaData().getDatabaseProductVersion());report.put("driver",c.getMetaData().getDriverVersion());}
  }
  @BeforeEach void fixture(TestInfo info) {
    result=new LinkedHashMap<>();result.put("name",info.getTestMethod().orElseThrow().getName());result.put("status","FAILED");cases.add(result);
    group="cd-"+UUID.randomUUID();unrelated="other-"+UUID.randomUUID();guard="guard-"+UUID.randomUUID();
    manager=new DataSourceTransactionManager(source);otherManager=new DataSourceTransactionManager(other);
    raw=new CooldownObservation();jdbc.cooldownObservation=raw;
    probe=new AcquisitionTransactionProbe(jdbc,view,guard,"modelSyncCooldownApply");probe.armed=true;
    manager.setTransactionExecutionListeners(List.of(raw,probe));gate=create(manager,jdbc,CooldownRetry::sleep);
    second=create(otherManager,new JdbcTemplate(other),CooldownRetry::sleep);
    gate.register(group,Duration.ofMillis(20));gate.register(unrelated,Duration.ofMillis(20));gate.register(guard,Duration.ofMillis(1));write();
  }
  private JdbcQuotaGate create(DataSourceTransactionManager m,JdbcTemplate j,LongConsumer pause) {
    return new JdbcQuotaGate(j,m,clock,JdbcQuotaLockPolicy.ORACLE_NOWAIT,JdbcInsertPolicy.ORACLE_26_NOWAIT,JdbcCooldownPolicy.DURABLE,new CooldownRetry(()->.5,pause));
  }
  private CooldownRequest request(){return new CooldownRequest(UUID.randomUUID().toString(),group,clock.instant().plusSeconds(30));}
  private Future<?> hold(CooldownCommitLatch latch,Callable<?> action)throws Exception {
    otherManager.setTransactionExecutionListeners(List.of(latch));var future=executor.submit(action);assertTrue(latch.held.await(5,TimeUnit.SECONDS));return future;
  }
  private void observations(int refusals) {
    var txs=List.copyOf(probe.transactions);result.put("refusal_transactions",txs);
    assertEquals(refusals,txs.stream().filter(t->"ROLLED_BACK".equals(t.get("completion"))).count());
    for(var t:txs)if("ROLLED_BACK".equals(t.get("completion"))) {
      assertTrue(t.get("actual_sql").toString().toLowerCase(Locale.ROOT).endsWith("nowait"));
      for(var key:List.of("server_before_rollback","server_after_rollback")) {
        var state=(Map<?,?>)t.get(key);assertEquals(0,((Number)state.get("LOCK_WAIT")).intValue());
        assertEquals(key.equals("server_before_rollback")?1:0,((Number)state.get("TRANSACTION_ACTIVE")).intValue());
      }
    }
    // Every refused attempt wrote the guard in its transaction. Fresh observation proves rollback.
    assertEquals(0L,view.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group=?",Long.class,guard));
    result.put("blocker_released_for_refusal_proof",false);result.put("guard_rolled_back",true);
  }
  private void healthy() {
    assertEquals(0,source.getHikariPoolMXBean().getActiveConnections());assertEquals(1,jdbc.queryForObject("select 1 from dual",Integer.class));
    result.put("runtime_pool_active",source.getHikariPoolMXBean().getActiveConnections());result.put("runtime_connection_healthy",true);
  }
  private void pass(){healthy();result.put("physical_transactions",raw.snapshot());result.put("detached_sql_failures",raw.failures());result.put("status","PASS");write();}
  private void write(){try{OracleMeasurements.write(path,report);}catch(Exception e){throw new IllegalStateException("Cannot retain cooldown evidence",e);}}

  @Test void heldQuotaReleaseWithinBudgetReusesAbsoluteDeadline()throws Exception {
    var latch=new CooldownCommitLatch("modelSyncQuota");var held=hold(latch,()->second.acquire(group));var request=request();
    gate=create(manager,jdbc,ms->{assertEquals(0,source.getHikariPoolMXBean().getActiveConnections());observations(1);latch.release.countDown();try{held.get(5,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}});
    try {
      long start=System.nanoTime();var receipt=gate.publishCooldown(request);result.put("client_ms",(System.nanoTime()-start)/1_000_000);
      assertEquals(CooldownState.APPLIED,receipt.state());assertEquals(request,receipt.request());
      assertEquals(2L,raw.snapshot().get("apply_attempts"));assertEquals(1L,raw.snapshot().get("apply_refusals"));
      assertEquals(request.until().toEpochMilli(),view.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group=?",Long.class,group));pass();
    } finally {latch.release.countDown();held.get(5,TimeUnit.SECONDS);}
  }

  @Test void budgetExhaustionRetainsSharedPendingAndOtherProviderProgress()throws Exception {
    var latch=new CooldownCommitLatch("modelSyncQuota");var held=hold(latch,()->second.acquire(group));var request=request();
    try {
      long start=System.nanoTime();var receipt=gate.publishCooldown(request);result.put("client_ms",(System.nanoTime()-start)/1_000_000);
      assertEquals(CooldownState.DURABLE_PENDING,receipt.state());assertTrue(receipt.durable());observations(4);healthy();
      assertEquals("PENDING",view.queryForObject("select publication_status from MS_COOLDOWN where operation_id=?",String.class,request.operationId()));
      assertEquals(request.until(),create(manager,jdbc,CooldownRetry::sleep).nextAdmission(group).orElseThrow());
      assertThrows(LocalContention.class,()->gate.acquire(group));assertTrue(gate.acquire(unrelated).granted());
      // The compliant competing admission executed its pending guard before acceptance: one in-flight grant.
      result.put("overlapping_admissions_before_pending_guard",1);result.put("unsafe_new_owner_calls",0);result.put("pending_deadline_ms",request.until().toEpochMilli());
      assertFalse(held.isDone());latch.release.countDown();held.get(5,TimeUnit.SECONDS);
      var fresh=create(otherManager,new JdbcTemplate(other),CooldownRetry::sleep);assertFalse(fresh.acquire(group).granted());
      assertEquals(request.until(),fresh.nextAdmission(group).orElseThrow());assertEquals(0,fresh.recoverCooldowns(group,1));pass();
    } finally {latch.release.countDown();held.get(5,TimeUnit.SECONDS);}
  }

  @Test void duplicateInsertRefusalIsUnrecordedUntilCompetitorCommitOrRollback()throws Exception {
    probe=new AcquisitionTransactionProbe(jdbc,view,guard,"modelSyncCooldownAccept");probe.armed=true;manager.setTransactionExecutionListeners(List.of(raw,probe));
    for(boolean rollback:List.of(false,true)) {
      var request=request();var latch=new CooldownCommitLatch("modelSyncCooldownAccept");latch.rollback=rollback;
      var store=new JdbcCooldownPublications(new JdbcTemplate(other),otherManager,clock,JdbcQuotaLockPolicy.ORACLE_NOWAIT,JdbcInsertPolicy.ORACLE_26_NOWAIT,new CooldownRetry(()->.5,CooldownRetry::sleep));
      var held=hold(latch,()->store.accept(request));
      try {
        var failure=assertThrows(LocalContention.class,()->gate.publishCooldown(request));assertEquals(54,DetachedFailure.capture(failure).vendorCode());
        assertThrows(CooldownUnavailable.class,()->gate.acquire(group));assertTrue(gate.acquire(unrelated).granted());
        assertEquals(0,view.queryForObject("select count(*) from MS_COOLDOWN where operation_id=?",Integer.class,request.operationId()));assertFalse(held.isDone());
        latch.release.countDown();if(rollback)assertThrows(ExecutionException.class,()->held.get(5,TimeUnit.SECONDS));else held.get(5,TimeUnit.SECONDS);
        otherManager.setTransactionExecutionListeners(List.of());
        var fresh=create(otherManager,new JdbcTemplate(other),CooldownRetry::sleep);
        var actual=fresh.publishCooldown(request);assertEquals(CooldownState.APPLIED,actual.state());
        assertEquals(actual,fresh.publishCooldown(new CooldownRequest(request.operationId(),group,request.until().plusSeconds(60))));
        fresh.publishCooldown(new CooldownRequest(UUID.randomUUID().toString(),group,request.until().minusSeconds(10)));
        assertFalse(fresh.acquire(group).granted());assertFalse(fresh.nextAdmission(group).orElseThrow().isBefore(request.until()));
      } finally {latch.release.countDown();try{held.get(5,TimeUnit.SECONDS);}catch(ExecutionException expected){assertTrue(rollback);}}
    }
    observations(8);assertEquals(8L,raw.snapshot().get("accept_refusals"));result.put("duplicate_competitor_commit_and_rollback",true);pass();
  }

  @Test void durablePendingSurvivesForcedOwnedProcessTermination()throws Exception {
    var request=request();Path checkpoint=path.getParent().resolve("cooldown-checkpoint-"+request.operationId());
    Path log=path.getParent().resolve("cooldown-process-"+request.operationId()+".log");
    var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),"-Xmx384m","-cp",System.getProperty("java.class.path"),CooldownPendingProcess.class.getName(),request.operationId(),group,Long.toString(request.until().toEpochMilli()),checkpoint.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    try {
      long bound=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!Files.isRegularFile(checkpoint)&&process.isAlive()&&System.nanoTime()<bound)Thread.sleep(10);
      assertTrue(Files.isRegularFile(checkpoint));var marker=JsonMapper.builder().build().readValue(Files.readString(checkpoint),Map.class);assertEquals("DURABLE_PENDING",marker.get("state"));assertTrue(process.isAlive());
      var session=(Map<?,?>)marker.get("session");
      process.destroyForcibly();assertTrue(process.waitFor(5,TimeUnit.SECONDS));assertFalse(process.isAlive());
      long serverBound=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(view.queryForObject("select count(*) from v$session where sid=? and serial#=?",Integer.class,session.get("SID"),session.get("SERIAL#"))!=0 && System.nanoTime()<serverBound)Thread.sleep(10);
      assertEquals(0,view.queryForObject("select count(*) from v$session where sid=? and serial#=?",Integer.class,session.get("SID"),session.get("SERIAL#")));result.put("owned_server_session_absent",true);
      assertEquals("PENDING",view.queryForObject("select publication_status from MS_COOLDOWN where operation_id=?",String.class,request.operationId()));
      var fresh=create(otherManager,new JdbcTemplate(other),CooldownRetry::sleep);assertFalse(fresh.acquire(group).granted());
      assertEquals("APPLIED",view.queryForObject("select publication_status from MS_COOLDOWN where operation_id=?",String.class,request.operationId()));
      result.put("owned_process_exit",process.exitValue());result.put("checkpoint_after_committed_acceptance",true);result.put("owned_process_absent",true);pass();
    } finally {if(process.isAlive()){process.destroyForcibly();assertTrue(process.waitFor(5,TimeUnit.SECONDS));}Files.deleteIfExists(checkpoint);}
  }

  @Test void committedCooldownPrecedesFailedMetadataAndRecoveryDoesNotRefetch()throws Exception {
    probe.armed=false;
    String binding="cooldown-metadata-"+UUID.randomUUID();var key=new WorkKey(binding,new SubjectKey("synthetic","one"));
    var store=OracleQualificationStores.work(jdbc);var tx=new SpringLocalTransactions(manager);var calls=new AtomicInteger();
    var model=ModelBinding.builder(binding,String.class).boundary("synthetic").owner("local",r->{calls.incrementAndGet();throw new RateLimitedAfter(Duration.ofSeconds(30));})
        .replica(new io.modelsync.testkit.MemoryProjectionStore<String>()).quota(group).fingerprint(Digests::sha256Fields).build();
    var c=new SyncCoordinator(new ModelRegistry(List.of(model)),store,tx,gate,clock,SyncOptions.defaults(),WeightedPriorities.defaults(),()->.5);
    tx.required(()->store.enqueue(key,Priority.NORMAL,true,clock.instant()));var lease=c.claim().orElseThrow();var plan=c.prepare(lease).orElseThrow();
    var original=assertThrows(RateLimitedAfter.class,()->((BlockingOwner<?>)model.owner()).fetch(plan.request()));
    try(var blocker=other.getConnection()) {
      blocker.setAutoCommit(false);try(var s=blocker.prepareStatement("select work_id from MS_WORK where work_id=? for update nowait")){s.setString(1,key.storageId());try(var r=s.executeQuery()){assertTrue(r.next());}}
      assertThrows(LocalContention.class,()->c.failed(lease,original));
      assertEquals("APPLIED",view.queryForObject("select publication_status from MS_COOLDOWN where quota_group=?",String.class,group));
      assertEquals("LEASED",view.queryForObject("select work_status from MS_WORK where work_id=?",String.class,key.storageId()));
      assertEquals(0,view.queryForObject("select count(*) from MS_STATE where work_id=?",Integer.class,key.storageId()));healthy();blocker.rollback();
    }
    long deadline=view.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group=?",Long.class,group);
    c.failed(lease,original);assertEquals(deadline,view.queryForObject("select next_at_ms from MS_WORK where work_id=?",Long.class,key.storageId()));
    assertEquals("DEFERRED",view.queryForObject("select last_error_code from MS_WORK where work_id=?",String.class,key.storageId()));
    assertEquals(1,calls.get());assertEquals(1,view.queryForObject("select count(*) from MS_COOLDOWN where quota_group=?",Integer.class,group));
    result.put("physical_owner_calls",calls.get());result.put("canonical_deadline_ms",deadline);result.put("metadata_refusal_after_cooldown_commit",true);
    jdbc.update("delete from MS_WORK where work_id=?",key.storageId());pass();
  }

  @AfterEach void cleanup() {
    probe.armed=false;healthy();jdbc.cooldownObservation=null;
    jdbc.update("delete from MS_COOLDOWN where quota_group in (?,?,?)",group,unrelated,guard);jdbc.update("delete from MS_QUOTA where quota_group in (?,?,?)",group,unrelated,guard);
    result.put("owned_rows_remaining",view.queryForObject("select count(*) from MS_COOLDOWN where quota_group in (?,?,?)",Integer.class,group,unrelated,guard));write();
  }
  @AfterAll void close()throws Exception {
    executor.shutdownNow();boolean terminated=executor.awaitTermination(5,TimeUnit.SECONDS);report.put("owned_executor_terminated",terminated);
    for(var s:List.of(source,other,observer))s.close();report.put("owned_pools_closed",true);
    report.put("status",terminated&&cases.size()==5&&cases.stream().allMatch(c->"PASS".equals(c.get("status")))?"PASS":"FAILED");write();assertTrue(terminated);
  }
}
