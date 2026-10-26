package example.consumer;

import static org.junit.jupiter.api.Assertions.*;
import com.zaxxer.hikari.HikariDataSource;
import io.modelsync.core.application.SyncGateway;
import io.modelsync.core.domain.SubjectKey;
import io.modelsync.core.domain.WorkKey;
import io.modelsync.reactor.ReactorSyncWorker;
import io.modelsync.reactor.WorkerShutdownStatus;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;

class SecuredConsumerIT {
  private final List<Map<String,Object>> negatives=new ArrayList<>();
  private final List<Map<String,Object>> committed=new ArrayList<>();
  private ConfigurableApplicationContext host;
  private JdbcTemplate observer;
  private SecurityOwner owner;
  private AccessObservation access;
  private ConsumerObservation observed;
  private EventWitness events;

  @Test void authenticatedFacadesEnforceTenantAndOperationIsolation() throws Exception {
    assertEquals("DEDICATED_TEST_SCHEMA",System.getenv("MODELSYNC_ORACLE_TEST_SCHEMA_ACK"));
    assertEquals("DISPOSABLE_NONPRODUCTION",System.getenv("MODELSYNC_FULL_ENV_ACK"));
    String mode=System.getProperty("consumer.mode");
    var receipt=new LinkedHashMap<String,Object>();receipt.put("status","FAILED");receipt.put("mode",mode);receipt.put("pid",ProcessHandle.current().pid());
    for(var pair:Map.of("run_id","SECURITY_RUN_ID","fixture_source","SECURITY_FIXTURE_SOURCE","fixture_tree","SECURITY_FIXTURE_TREE",
        "artifact_source","SECURITY_ARTIFACT_SOURCE","manifest_sha256","SECURITY_MANIFEST_SHA256").entrySet())receipt.put(pair.getKey(),System.getenv(pair.getValue()));
    receipt.put("negative_cases",negatives);receipt.put("committed_phases",committed);
    observer=new JdbcTemplate(new DriverManagerDataSource(System.getenv("MODELSYNC_ORACLE_URL"),System.getenv("MODELSYNC_ORACLE_USER"),System.getenv("MODELSYNC_ORACLE_PASSWORD")));
    boolean table=false;HikariDataSource pool=null;ReactorSyncWorker worker=null;
    try {
      observer.execute("create table CONSUMER_ITEM (storage_key varchar2(64 char) primary key,tenant_id varchar2(128 char) not null,source_id varchar2(256 char) not null,local_version number(19) not null,item_value varchar2(80 char),expires_at timestamp(6) with time zone,constraint CONSUMER_ITEM_UQ unique(tenant_id,source_id))");table=true;
      try(var endpoint=new SecurityOwner()) {
        owner=endpoint;host=SecurityHost.start(mode,true,owner.url());pool=host.getBean(HikariDataSource.class);worker=host.getBean(ReactorSyncWorker.class);
        access=host.getBean(AccessObservation.class);observed=host.getBean(ConsumerObservation.class);events=host.getBean(EventWitness.class);
        SecurityHost.inspect(host,receipt);receipt.put("artifact_resolution",true);receipt.put("enlistment",true);
        assertEquals("7.1.1",org.springframework.security.core.SpringSecurityCoreVersion.getVersion());receipt.put("spring_security","7.1.1");
        String base="http://127.0.0.1:"+host.getEnvironment().getRequiredProperty("local.server.port");
        try(var alice=new SecurityHttpClient(base,"alice");var bob=new SecurityHttpClient(base,"bob");
            var reader=new SecurityHttpClient(base,"reader");var refresher=new SecurityHttpClient(base,"refresher");
            var empty=new SecurityHttpClient(base,"empty");var anonymous=new SecurityHttpClient(base,null)) {
          submit(alice,"alpha","refresh");await("alpha","alpha-A",0,1);
          submit(bob,"beta","refresh");await("beta","beta-A",0,1);
          assertEquals("alpha-A",alice.send("GET",path("alpha","item","shared","read"),false).body());
          assertEquals("beta-A",bob.send("GET",path("beta","item","shared","read"),false).body());
          assertNotEquals(key("alpha"),key("beta"));
          // Every denial is measured with an already fresh protected cache present.
          deny("unauthenticated-read",anonymous,"alpha","item","shared","read",false,401);
          deny("unauthenticated-submit",anonymous,"alpha","item","shared","refresh",false,"mvc".equals(mode)?401:403);
          deny("missing-csrf",alice,"alpha","item","shared","refresh",false,"mvc".equals(mode)?401:403);
          for(String op:List.of("read","refresh","event")) {
            deny("cross-tenant-"+op,alice,"beta","item","shared",op,true,403);
            deny("unknown-tenant-"+op,alice,"unknown","item","shared",op,true,403);
            deny("wrong-binding-"+op,alice,"alpha","unknown","shared",op,true,403);
            deny("wrong-subject-"+op,alice,"alpha","item","other",op,true,403);
            deny("missing-authority-"+op,empty,"alpha","item","shared",op,true,403);
          }
          for(String op:List.of("refresh","event"))deny("reader-cannot-"+op,reader,"alpha","item","shared",op,true,403);
          for(String op:List.of("read","event"))deny("refresher-cannot-"+op,refresher,"alpha","item","shared",op,true,403);
          deny("unknown-operation",alice,"alpha","item","shared","delete",true,403);
          for(String op:List.of("read","refresh","event"))denyService("direct-anonymous-"+op,null,op,"alpha");
          for(String op:List.of("refresh","event"))denyService("direct-reader-"+op,"reader",op,"alpha");
          for(String op:List.of("read","event"))denyService("direct-cross-"+op,"bob",op,"alpha");
          assertAll("Exact negative HTTP statuses", negatives.stream().filter(n->n.containsKey("http_status"))
              .map(n->(org.junit.jupiter.api.function.Executable)()->assertEquals(n.get("expected_status"),n.get("http_status"),n.get("case").toString())));
          owner.values.put("alpha","alpha-B");submit(alice,"alpha","event");await("alpha","alpha-B",1,2);
          owner.values.put("beta","beta-B");submit(bob,"beta","refresh");await("beta","beta-B",1,2);
          assertEquals("alpha-B",reader.send("GET",path("alpha","item","shared","read"),false).body());
          if("webflux".equals(mode)) {
            var before=snapshot();var calls=new ArrayList<CompletableFuture<java.net.http.HttpResponse<String>>>();
            for(int i=0;i<32;i++)calls.add((i%2==0?alice:bob).asyncRead(path(i%2==0?"alpha":"beta","item","shared","read")));
            CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).get(10,java.util.concurrent.TimeUnit.SECONDS);
            for(int i=0;i<32;i++){assertEquals(200,calls.get(i).get().statusCode());assertEquals(i%2==0?"alpha-B":"beta-B",calls.get(i).get().body());}
            var after=snapshot();assertEquals(before.rows(),after.rows());assertEquals(before.ownerCalls(),after.ownerCalls());
            assertEquals(before.access().reads()+32,after.access().reads());assertEquals(before.access().enqueues(),after.access().enqueues());
            assertEquals(before.events(),after.events());receipt.put("concurrent_identity_checks",32);
          }
          assertTrue(access.accepted.stream().allMatch(a->a.tenant().equals(a.principal().equals("bob")?"beta":"alpha")));
          if("webflux".equals(mode))assertTrue(access.accepted.stream().allMatch(a->a.thread().startsWith("model-sync-jpa")));
          assertEquals(Set.of("ORACLE_NOWAIT"),observed.lookupPolicies);
          rollbackControl();receipt.put("rollback",true);
          assertEquals(4,owner.calls.get());assertEquals(4,observed.ownerCalls.get());assertEquals(1,events.published.get());
          assertEquals(Set.of("known-id-service:alpha:item","known-id-service:beta:item"),observed.ownerScopes);
          receipt.put("trusted_worker_scope",true);receipt.put("owner_scopes",observed.ownerScopes);
          receipt.put("owner_calls",owner.calls.get());receipt.put("committed_reconciliations",committed.size());
          receipt.put("accepted_invocations",List.copyOf(access.accepted));receipt.put("permission_lookups",host.getBean(ResourcePolicy.class).lookups.get());
          receipt.put("access_counts",access.counts());receipt.put("actual_event_publications",events.published.get());
        }
        host.close();host=null;
        var stop=worker.shutdown();assertEquals(WorkerShutdownStatus.DRAINED,stop.status());assertEquals(0,worker.physicalOperations());
        assertTrue(pool.isClosed());receipt.put("pool_closed",true);
        try(var disabled=SecurityHost.start(mode,false,owner.url())) {
          assertTrue(disabled.getBeansOfType(SyncGateway.class).isEmpty());assertTrue(disabled.getBeansOfType(ReactorSyncWorker.class).isEmpty());
          assertTrue(disabled.getBeansOfType(ConsumerFacade.class).isEmpty());assertTrue(disabled.getBeansOfType(ConsumerModelAccess.class).isEmpty());
          var disabledPool=disabled.getBean(HikariDataSource.class);disabled.close();assertTrue(disabledPool.isClosed());
        }
        receipt.put("disabled_mode",true);
      }
      long stop=System.nanoTime()+Duration.ofSeconds(3).toNanos();
      while(SecurityHost.ownedThreads()!=0&&System.nanoTime()<stop)Thread.sleep(20);
      receipt.put("owned_threads_remaining",SecurityHost.ownedThreads());assertEquals(0,SecurityHost.ownedThreads());
      assertEquals(0,observed.ownerActive.get());receipt.put("active_owner_operations",observed.ownerActive.get());
      receipt.put("status","PASS");
    }catch(Exception|AssertionError failure){receipt.put("failure_type",failure.getClass().getName());throw failure;
    }finally{
      if(host!=null)host.close();
      if(observed!=null){receipt.put("bounded_sql",List.copyOf(observed.sql));receipt.put("sql_dropped",observed.dropped.get());receipt.put("persistence_threads",observed.persistenceThreads);receipt.put("owner_threads",observed.ownerThreads);}
      try {
        if(table){for(String tenant:List.of("alpha","beta")){observer.update("delete from MS_STATE where work_id=?",key(tenant));observer.update("delete from MS_WORK where work_id=?",key(tenant));}
          observer.update("delete from MS_COOLDOWN where quota_group='consumer-owner'");observer.update("delete from MS_QUOTA where quota_group='consumer-owner'");observer.execute("drop table CONSUMER_ITEM purge");
          assertEquals(0,observer.queryForObject("select count(*) from MS_WORK where binding_id='item'",Integer.class));receipt.put("cleanup",true);}
      }catch(Exception|AssertionError cleanup){receipt.put("status","FAILED");receipt.put("cleanup_failure",cleanup.getClass().getName());throw cleanup;
      }finally{Files.createDirectories(Path.of("target/consumer-evidence"));Files.writeString(Path.of("target/consumer-evidence/security.json"),new tools.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(receipt));}
    }
  }
  private static String key(String tenant){return new WorkKey("item",new SubjectKey(tenant,"shared")).storageId();}
  private static String path(String tenant,String binding,String subject,String op){return "/models/"+tenant+"/"+binding+"/"+subject+("read".equals(op)?"":"/"+op);}
  private void submit(SecurityHttpClient client,String tenant,String op)throws Exception{assertEquals(200,client.send("POST",path(tenant,"item","shared",op),true).statusCode());}
  private List<Map<String,Object>> rows(){return observer.queryForList("select w.work_id,w.tenant_id,w.subject_id,w.generation,w.work_status,w.requested_at_ms,w.next_at_ms,w.lease_token,w.attempts,s.validated_at_ms,s.reconciled_at_ms,s.source_changed_at_ms,s.owner_validator,s.outcome,p.item_value,p.local_version from MS_WORK w left join MS_STATE s on s.work_id=w.work_id left join CONSUMER_ITEM p on p.storage_key=w.work_id where w.binding_id='item' order by w.tenant_id");}
  private SecuritySnapshot snapshot(){return new SecuritySnapshot(access.counts(),observed.mappingReads.get(),owner.calls.get(),events.published.get(),rows());}
  private void deny(String name,SecurityHttpClient client,String tenant,String binding,String subject,String op,boolean csrf,int status)throws Exception{
    var before=snapshot();var response=client.send("read".equals(op)?"GET":"POST",path(tenant,binding,subject,op),csrf);
    var after=snapshot();
    var fact=new LinkedHashMap<String,Object>();fact.put("case",name);fact.put("http_status",response.statusCode());fact.put("expected_status",status);fact.put("authentication_challenge",response.headers().firstValue("WWW-Authenticate").isPresent());fact.put("before",before);fact.put("after",after);fact.put("side_effects_unchanged",before.equals(after));negatives.add(fact);
    assertEquals(before,after,name+" had protected side effects");
    assertFalse(response.body().contains("alpha-A"));assertFalse(response.body().contains("beta-A"));
  }
  private void denyService(String name,String user,String op,String tenant){
    var before=snapshot();RuntimeException denied=AuthorizationFailures.denied(()->FacadeProbe.call(host,user,op,tenant,"item","shared"));
    var after=snapshot();negatives.add(Map.of("case",name,"failure_type",denied.getClass().getName(),"side_effects_unchanged",before.equals(after),"before",before,"after",after));assertEquals(before,after);
  }
  private void await(String tenant,String value,long revision,long generation)throws Exception{
    long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();Map<String,Object> row=Map.of();
    while(System.nanoTime()<deadline){row=rows().stream().filter(r->tenant.equals(r.get("TENANT_ID"))).findFirst().orElse(Map.of());
      if(value.equals(row.get("ITEM_VALUE"))&&"IDLE".equals(row.get("WORK_STATUS")))break;Thread.sleep(25);}
    committed.add(new LinkedHashMap<>(row));assertEquals(value,row.get("ITEM_VALUE"));assertEquals(value,row.get("OWNER_VALIDATOR"));
    assertEquals("APPLIED",row.get("OUTCOME"));assertEquals("IDLE",row.get("WORK_STATUS"));assertEquals(revision,((Number)row.get("LOCAL_VERSION")).longValue());assertEquals(generation,((Number)row.get("GENERATION")).longValue());
  }
  private void rollbackControl(){
    var before=rows();var em=org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(host.getBean(jakarta.persistence.EntityManagerFactory.class));
    new org.springframework.transaction.support.TransactionTemplate(host.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->{
      host.getBean(SyncGateway.class).model("item",ItemValue.class).request("alpha","shared",io.modelsync.core.Priority.NORMAL,true);
      em.find(ItemEntity.class,key("alpha")).value="UNCOMMITTED";em.flush();tx.setRollbackOnly();
    });assertEquals(before,rows());
  }
}
