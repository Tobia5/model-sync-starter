package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariDataSource;
import io.modelsync.core.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.*;
import io.modelsync.jdbc.JdbcQuotaGate;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Actual compliant first creators; default waits are observations, not cancellation qualification. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OracleFirstCreationIT {
  private HikariDataSource observer;
  private JdbcTemplate view;
  private ConfigurableApplicationContext appA,appB;
  private CreationTransactionProbe a,b;
  private final ExecutorService executor=Executors.newFixedThreadPool(2,r->new Thread(r,"modelsync-creation-owned"));
  private final AtomicInteger physical=new AtomicInteger();
  private final List<String> owned=new ArrayList<>(), groups=new ArrayList<>();
  private final List<Map<String,Object>> cases=new ArrayList<>();
  private Map<String,Object> report,result;
  private boolean tableOwned;

  @BeforeAll void open() throws Exception {
    report=FaultEvidence.report("compliant-first-creation");report.put("cases",cases);report.put("observation_ms",5000);
    observer=RecoveryConfiguration.source(System.getenv("MODELSYNC_ORACLE_URL"),2,10000);view=new JdbcTemplate(observer);view.setQueryTimeout(2);
    assertEquals(0,view.queryForObject("select count(*) from user_tables where table_name='SAMPLE_CUSTOMER'",Integer.class));
    new ResourceDatabasePopulator(new ClassPathResource("sample-oracle.sql")).execute(observer);tableOwned=true;
    try(var c=observer.getConnection()) {var m=c.getMetaData();report.put("oracle",m.getDatabaseProductVersion());report.put("oracle_major",m.getDatabaseMajorVersion());report.put("oracle_minor",m.getDatabaseMinorVersion());report.put("driver",m.getDriverVersion());}
    report.put("jdk",System.getProperty("java.runtime.version"));report.put("hibernate",org.hibernate.Version.getVersionString());write();
  }
  private ConfigurableApplicationContext app(boolean nativeInsert) {
    return new SpringApplicationBuilder(LockPolicyOracleConfiguration.class).web(WebApplicationType.NONE).profiles("lock-policy-oracle")
        .run("--spring.jpa.hibernate.ddl-auto=validate","--spring.main.banner-mode=off","--logging.level.root=WARN",
            "--model-sync.enabled=true","--model-sync.worker-enabled=false","--model-sync.aop.enabled=false",
            "--model-sync.jdbc.point-lock-policy=ORACLE_NOWAIT","--model-sync.jpa.lock-policy=ORACLE_NOWAIT",
            "--model-sync.jdbc.quota-lock-policy=ORACLE_NOWAIT","--model-sync.quotas.policy-owner=1ms",
            "--model-sync.jdbc.insert-policy="+(nativeInsert?"ORACLE_26_NOWAIT":"DEFAULT"));
  }
  private void boot(String name,boolean nativeInsert,String transactionName) throws Exception {
    assertEquals(0,physical.get());result=new LinkedHashMap<>();result.put("name",name);result.put("status","FAILED");cases.add(result);write();
    appA=app(nativeInsert);appB=app(nativeInsert);
    a=probe(appA,transactionName);b=probe(appB,transactionName);a.pause=true;
    result.put("insert_policy",nativeInsert?"ORACLE_26_NOWAIT":"DEFAULT");result.put("runtime_pool_each",3);result.put("observer_pool",2);
    result.put("acquire_ms",1000);result.put("connect_ms",1500);result.put("read_ms",10000);result.put("manager_timeout_seconds",appA.getBean(JpaTransactionManager.class).getDefaultTimeout());
    result.put("a_sessions",appA.getBean(LockPolicyObservation.class).runtimeSessions);result.put("b_sessions",appB.getBean(LockPolicyObservation.class).runtimeSessions);
  }
  private CreationTransactionProbe probe(ConfigurableApplicationContext app,String name) {
    var p=new CreationTransactionProbe(new JdbcTemplate(app.getBean(HikariDataSource.class)),view,name);
    app.getBean(JpaTransactionManager.class).setTransactionExecutionListeners(List.of(p));return p;
  }
  private SubjectKey key() {var key=new SubjectKey("synthetic-creation",UUID.randomUUID().toString());owned.add(CustomerJpaMapping.storageId(key));return key;}
  private String request(ConfigurableApplicationContext app,SubjectKey key) {return app.getBean(SyncGateway.class).request("customer",key.tenant(),key.id(),Priority.URGENT,true);}
  private <T> T tx(ConfigurableApplicationContext app,String name,Callable<T> action) {
    var tx=new TransactionTemplate(app.getBean(JpaTransactionManager.class));tx.setName(name);
    return tx.execute(status->{try{return action.call();}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}});
  }
  private Future<CreationAttempt> start(CreationTransactionProbe p,Callable<String> action) {
    p.events.clear();
    return executor.submit(()->{long start=System.nanoTime();String ticket=null;Throwable failure=null;physical.incrementAndGet();
      try {ticket=action.call();}catch(Exception e){failure=e;}finally{physical.decrementAndGet();}
      var receipt=new LinkedHashMap<String,Object>(p.events);receipt.put("physical_return_ms",elapsed(start));
      receipt.put("failure_type",failure==null?"NONE":failure.getClass().getName());
      for(Throwable c=failure;c!=null;c=c.getCause()) if(c instanceof SQLException sql) {receipt.put("oracle_code",sql.getErrorCode());break;}
      return new CreationAttempt(receipt,ticket,failure);});
  }
  private CreationAttempt finished(Future<CreationAttempt> task,String label) throws Exception {
    var done=task.get(5,TimeUnit.SECONDS);result.put(label,done.evidence());write();return done;
  }
  private void ready() throws Exception {assertTrue(a.inserted.await(5,TimeUnit.SECONDS),"A must acknowledge its actual insertion before B starts");result.put("a_after_insert_before_commit",a.events.get("after_insert_before_commit"));write();}
  private void waiting(Future<CreationAttempt> task) throws Exception {
    OracleRecoveryIT.await(()->{if(b.sid==0)return false;var state=b.snapshot();return ((Number)state.get("LOCK_WAIT")).intValue()==1&&state.get("BLOCKING_SESSION") instanceof Number blocker&&blocker.intValue()==a.sid;},2500);
    assertFalse(task.isDone());var state=b.snapshot();result.put("b_while_a_unresolved",state);
    result.put("blocked_sql",view.queryForObject("select sql_fulltext from v$sql where sql_id=? and rownum=1",String.class,state.get("SQL_ID")));
    result.put("a_unresolved",!a.resolved);result.put("b_physically_complete_before_a_resolution",false);write();
  }
  private void refused(CreationAttempt attempt) {
    assertInstanceOf(LocalContention.class,attempt.failure());assertEquals(54,attempt.evidence().get("oracle_code"));
    assertEquals("ROLLED_BACK",attempt.evidence().get("completion"));assertNull(attempt.ticket());assertFalse(a.resolved);
    var before=(Map<?,?>)attempt.evidence().get("server_before_rollback");var after=(Map<?,?>)attempt.evidence().get("server_after_transaction");
    assertEquals(0,((Number)before.get("LOCK_WAIT")).intValue());assertEquals(0,((Number)after.get("LOCK_WAIT")).intValue());assertEquals(0,((Number)after.get("TRANSACTION_ACTIVE")).intValue());
    result.put("b_failed_transaction_accepted",false);
  }
  private void resolve(Future<CreationAttempt> first,boolean rollback) throws Exception {
    a.decide(rollback);result.put("a_resolution",rollback?"ROLLBACK":"COMMIT");var done=finished(first,"a");
    if(rollback)assertInstanceOf(CreationRollback.class,done.failure());else assertNull(done.failure());
    assertEquals(rollback?"ROLLED_BACK":"COMMITTED",done.evidence().get("completion"));result.put("a_accepted",!rollback);
  }
  private void emergency(Future<CreationAttempt> first,Future<CreationAttempt> second) throws Exception {
    boolean emergency=!a.resolved;result.put("emergency_release",emergency);
    if(emergency)a.decide(true);
    if(first!=null)first.get(12,TimeUnit.SECONDS);if(second!=null)second.get(12,TimeUnit.SECONDS);
    result.put("physical_after_resolution",physical.get());write();
  }
  private void work(boolean rollback,boolean nativeInsert) throws Exception {
    String name="work-"+(nativeInsert?"native":"default")+"-"+(rollback?"rollback":"commit");boot(name,nativeInsert,"creationWork");
    var key=key();var guard=key();String id=CustomerJpaMapping.storageId(key);
    a.verifyInserted=()->{assertEquals(1,a.jdbc.queryForObject("select count(*) from MS_WORK where work_id=?",Integer.class,id));assertEquals(0,view.queryForObject("select count(*) from MS_WORK where work_id=?",Integer.class,id));};
    var first=start(a,()->tx(appA,"creationWork",()->request(appA,key)));Future<CreationAttempt> second=null;
    try {
      ready();result.put("a_ticket_provisional",true);second=start(b,()->tx(appB,"creationWork",()->{request(appB,guard);return request(appB,key);}));
      if(nativeInsert) {refused(finished(second,"b_before_a_resolution"));assertEquals(0,view.queryForObject("select count(*) from MS_WORK where work_id=?",Integer.class,CustomerJpaMapping.storageId(guard)));}
      else waiting(second);
      assertEquals(0,view.queryForObject("select count(*) from MS_WORK where work_id=?",Integer.class,id));resolve(first,rollback);
      if(nativeInsert)second=start(b,()->tx(appB,"creationWork",()->request(appB,key)));
      var done=finished(second,"b_after_resolution");assertNull(done.failure());assertEquals(id,done.ticket());assertEquals("COMMITTED",done.evidence().get("completion"));
      var row=view.queryForMap("select generation,priority,force_refresh,work_status,tenant_id,subject_id from MS_WORK where work_id=?",id);
      assertEquals(rollback?1L:2L,((Number)row.get("GENERATION")).longValue());assertEquals(Priority.URGENT.ordinal(),((Number)row.get("PRIORITY")).intValue());assertEquals(1,((Number)row.get("FORCE_REFRESH")).intValue());
      assertEquals(key.tenant(),row.get("TENANT_ID"));assertEquals(key.id(),row.get("SUBJECT_ID"));assertEquals("WAITING",row.get("WORK_STATUS"));result.put("committed_work",row);
      result.put("classification",nativeInsert?"MEASURED_INSERT_REFUSAL":"UNBOUNDED_DEFAULT_WAIT");
    }finally{emergency(first,second);}pass();
  }
  private void quota(boolean rollback,boolean nativeInsert) throws Exception {
    String name="quota-"+(nativeInsert?"native":"default")+"-"+(rollback?"rollback":"commit");boot(name,nativeInsert,"modelSyncQuota");
    String group="creation-"+UUID.randomUUID();groups.add(group);var ga=appA.getBean(JdbcQuotaGate.class);var gb=appB.getBean(JdbcQuotaGate.class);
    a.verifyInserted=()->{assertEquals(1,a.jdbc.queryForObject("select count(*) from MS_QUOTA where quota_group=?",Integer.class,group));assertEquals(0,view.queryForObject("select count(*) from MS_QUOTA where quota_group=?",Integer.class,group));};
    var first=start(a,()->{ga.register(group,Duration.ofMillis(17));return null;});Future<CreationAttempt> second=null;
    try {
      ready();second=start(b,()->{gb.register(group,Duration.ofMillis(17));return null;});
      if(nativeInsert)refused(finished(second,"b_before_a_resolution"));else waiting(second);
      assertEquals(0,view.queryForObject("select count(*) from MS_QUOTA where quota_group=?",Integer.class,group));resolve(first,rollback);
      if(nativeInsert)second=start(b,()->{gb.register(group,Duration.ofMillis(17));return null;});
      var done=finished(second,"b_after_resolution");assertNull(done.failure());assertEquals("COMMITTED",done.evidence().get("completion"));
      var row=view.queryForMap("select spacing_ms,next_allowed_ms from MS_QUOTA where quota_group=?",group);
      assertEquals(17L,((Number)row.get("SPACING_MS")).longValue());assertEquals(0L,((Number)row.get("NEXT_ALLOWED_MS")).longValue());
      assertEquals(1,view.queryForObject("select count(*) from MS_QUOTA where quota_group=?",Integer.class,group));
      assertThrows(IllegalStateException.class,()->gb.register(group,Duration.ofMillis(18)));result.put("committed_configuration",row);
      result.put("classification",nativeInsert?"MEASURED_INSERT_REFUSAL":"UNBOUNDED_DEFAULT_WAIT");
    }finally{emergency(first,second);}pass();
  }
  @SuppressWarnings("unchecked")
  private void fence(boolean rollback) throws Exception {
    boot("projection-state-fence-"+(rollback?"rollback":"commit"),false,"creationResult");var key=key();var guard=key();String id=CustomerJpaMapping.storageId(key);request(appA,key);
    var ca=appA.getBean(SyncCoordinator.class);var cb=appB.getBean(SyncCoordinator.class);var prepared=ca.prepare(ca.claim().orElseThrow()).orElseThrow();
    var fetched=((BlockingOwner<?>)prepared.binding().owner()).fetch(prepared.request());
    a.verifyInserted=()->{for(String table:List.of("SAMPLE_CUSTOMER","MS_STATE")){String column=table.equals("MS_STATE")?"work_id":"storage_key";assertEquals(1,a.jdbc.queryForObject("select count(*) from "+table+" where "+column+"=?",Integer.class,id));assertEquals(0,view.queryForObject("select count(*) from "+table+" where "+column+"=?",Integer.class,id));}};
    var first=start(a,()->tx(appA,"creationResult",()->{ca.finish(prepared,fetched);return null;}));Future<CreationAttempt> second=null;
    var enteredMapping=new AtomicBoolean();var mapping=(ProjectionStore<Customer>)appB.getBean(ModelRegistry.class).get("customer").projection();
    try {
      ready();assertFalse(cb.runOne(),"A held lease cannot be claimed by another compliant worker");
      b=probe(appB,"creationBusiness");
      second=start(b,()->tx(appB,"creationBusiness",()->{request(appB,guard);String ticket=request(appB,key);enteredMapping.set(true);mapping.compareAndSet(key,null,new Customer("producer","ACTIVE"),null);return ticket;}));
      refused(finished(second,"b_before_a_resolution"));assertFalse(enteredMapping.get());assertEquals(0,view.queryForObject("select count(*) from MS_WORK where work_id=?",Integer.class,CustomerJpaMapping.storageId(guard)));
      assertTrue(appB.getBean(LockPolicyObservation.class).sql.isEmpty(),"Work fence must stop B before JPA creation SQL");result.put("b_mapping_entered",false);
      resolve(first,rollback);
      assertEquals(rollback?0:1,view.queryForObject("select count(*) from SAMPLE_CUSTOMER where storage_key=?",Integer.class,id));assertEquals(rollback?0:1,view.queryForObject("select count(*) from MS_STATE where work_id=?",Integer.class,id));
      tx(appB,"creationBusiness",()->{request(appB,key);var existing=mapping.load(key).orElse(null);assertTrue(mapping.compareAndSet(key,existing==null?null:existing.revision(),new Customer("producer","ACTIVE"),null));return null;});
      assertEquals(rollback?0L:1L,view.queryForObject("select local_version from SAMPLE_CUSTOMER where storage_key=?",Long.class,id));
      if(rollback){ca.finish(prepared,fetched);assertEquals("LOCAL_CONFLICT",view.queryForObject("select last_error_code from MS_WORK where work_id=?",String.class,id));assertEquals(0,view.queryForObject("select count(*) from MS_STATE where work_id=?",Integer.class,id));}
      var completed=new AtomicBoolean();OracleRecoveryIT.await(()->{if(!completed.get())completed.set(cb.runOne());return completed.get();},5000);
      assertEquals("IDLE",view.queryForObject("select work_status from MS_WORK where work_id=?",String.class,id));assertEquals(2L,view.queryForObject("select generation from MS_WORK where work_id=?",Long.class,id));
      assertEquals("after",view.queryForObject("select display_name from SAMPLE_CUSTOMER where storage_key=?",String.class,id));assertEquals(1,view.queryForObject("select count(*) from MS_STATE where work_id=?",Integer.class,id));
      assertThrows(LeaseLost.class,()->ca.finish(prepared,fetched));
      result.put("classification","SERIALIZED_BY_WORK_FENCE");result.put("a_jpa_sql",List.copyOf(appA.getBean(LockPolicyObservation.class).sql));
      result.put("state_insert_sql",view.query("select sql_id,sql_fulltext from v$sql where parsing_schema_name=user and lower(sql_text) like 'insert into ms_state%'",(r,n)->Map.of("sql_id",r.getString(1),"sql",r.getString(2))));
      assertEquals(2,appA.getBean(LockPolicyObservation.class).ownerCalls.get()+appB.getBean(LockPolicyObservation.class).ownerCalls.get());
      result.put("state_sql_scope","loaded SQL text; no cursor delta attribution");result.put("owner_reads",2);result.put("successful_result_commits",rollback?1:2);
    }finally{emergency(first,second);}pass();
  }
  @Test void defaultWorkCommit() throws Exception {work(false,false);}
  @Test void defaultWorkRollback() throws Exception {work(true,false);}
  @Test void defaultQuotaCommit() throws Exception {quota(false,false);}
  @Test void defaultQuotaRollback() throws Exception {quota(true,false);}
  @Test void firstProjectionAndStateCommitAreFenced() throws Exception {fence(false);}
  @Test void firstProjectionAndStateRollbackAreFenced() throws Exception {fence(true);}

  private void pass() throws Exception {
    assertEquals(0,physical.get());var pools=new ArrayList<Map<String,Object>>();
    for(var app:List.of(appA,appB)){var ds=app.getBean(HikariDataSource.class);assertEquals(0,ds.getHikariPoolMXBean().getActiveConnections());
      var states=new ArrayList<Map<String,Object>>();for(var id:app.getBean(LockPolicyObservation.class).runtimeSessions){var p=new CreationTransactionProbe(new JdbcTemplate(ds),view,"unused");p.sid=id.get("sid");p.serial=id.get("serial");var state=p.snapshot();assertEquals(0,((Number)state.get("LOCK_WAIT")).intValue());assertEquals(0,((Number)state.get("TRANSACTION_ACTIVE")).intValue());states.add(state);}
      assertEquals(1,new JdbcTemplate(ds).queryForObject("select 1 from dual",Integer.class));pools.add(Map.of("active",ds.getHikariPoolMXBean().getActiveConnections(),"idle",ds.getHikariPoolMXBean().getIdleConnections(),"waiting",ds.getHikariPoolMXBean().getThreadsAwaitingConnection(),"sessions",states));}
    result.put("pools_after",pools);result.put("physical_remaining",physical.get());result.put("status","PASS");write();
  }
  @AfterEach void clean() throws Exception {
    boolean clean=false;
    try{for(var app:new ConfigurableApplicationContext[]{appA,appB})if(app!=null){var ds=app.getBean(HikariDataSource.class);String pool=ds.getPoolName();app.close();assertTrue(ds.isClosed());FaultEvidence.awaitNoPoolThreads(pool);}appA=appB=null;assertEquals(0,physical.get());
      for(String id:owned){view.update("delete from MS_STATE where work_id=?",id);view.update("delete from MS_WORK where work_id=?",id);view.update("delete from SAMPLE_CUSTOMER where storage_key=?",id);}owned.clear();
      for(String group:groups)view.update("delete from MS_QUOTA where quota_group=?",group);groups.clear();view.update("delete from MS_QUOTA where quota_group='policy-owner'");clean=true;
    }finally{if(result!=null){result.put("cleanup_verified",clean);if(!clean)result.put("status","FAILED");}write();}
  }
  @AfterAll void close() throws Exception {
    executor.shutdown();boolean ended=executor.awaitTermination(12,TimeUnit.SECONDS);if(tableOwned)view.execute("drop table SAMPLE_CUSTOMER purge");if(observer!=null){observer.close();FaultEvidence.awaitNoPoolThreads(observer.getPoolName());}
    report.put("owned_executor_terminated",ended);report.put("physical_remaining",physical.get());report.put("owned_pools_closed",observer!=null&&observer.isClosed()&&appA==null&&appB==null);report.put("table_removed",tableOwned);
    report.put("status",ended&&physical.get()==0&&!cases.isEmpty()&&cases.stream().allMatch(c->"PASS".equals(c.get("status"))&&Boolean.TRUE.equals(c.get("cleanup_verified")))?"PASS":"FAILED");write();assertTrue(ended);
  }
  private void write() throws Exception {FaultEvidence.write("oracle-first-creation.json",report);}
  private static long elapsed(long start){return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);}
}
