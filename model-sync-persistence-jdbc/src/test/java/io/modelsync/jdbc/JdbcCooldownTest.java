package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;
import io.modelsync.core.port.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcCooldownTest {
  JdbcTemplate jdbc;
  DataSourceTransactionManager manager;
  final Clock clock=Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"),ZoneOffset.UTC);
  JdbcQuotaGate gate;
  @BeforeEach void open() {
    var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
    new ResourceDatabasePopulator(new ClassPathResource("model-sync/schema/h2-v2.sql")).execute(source);
    jdbc=new JdbcTemplate(source);manager=new DataSourceTransactionManager(source);gate=create();
    gate.register("owner",Duration.ofMillis(20));gate.register("other",Duration.ofMillis(20));
  }
  JdbcQuotaGate create() {return new JdbcQuotaGate(jdbc,manager,clock,JdbcQuotaLockPolicy.DEFAULT,JdbcInsertPolicy.DEFAULT,JdbcCooldownPolicy.DURABLE);}
  CooldownRequest request(String id,long seconds) {return new CooldownRequest(id,"owner",clock.instant().plusSeconds(seconds));}
  @Test void canonicalDeadlineRoundsUpOnceWithoutShorteningProviderPrecision() {
    var nanos=clock.instant().plusNanos(1);var value=new CooldownRequest("precision","owner",nanos);
    assertEquals(clock.instant().plusMillis(1),value.until());
    assertEquals(value,gate.publishCooldown(value).request());assertEquals(value,gate.publishCooldown(value).request());
    assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("update MS_COOLDOWN set publication_status='INVALID'"));
  }
  @Test void idempotentCanonicalReceiptsAndMonotonicOutOfOrderDeadlines() {
    var first=gate.publishCooldown(request("one",30));assertEquals(CooldownState.APPLIED,first.state());assertTrue(first.durable());
    assertEquals(first,gate.publishCooldown(request("one",90)),"Duplicate ACK cannot drift the original deadline");
    gate.publishCooldown(request("older",10));assertEquals(first.request().until(),gate.acquire("owner").retryAt());
    gate.publishCooldown(request("newer",40));gate.publishCooldown(request("oldest",1));
    assertEquals(request("newer",40).until(),gate.acquire("owner").retryAt());
    assertEquals(4,jdbc.queryForObject("select count(*) from MS_COOLDOWN",Integer.class));
    assertThrows(IllegalArgumentException.class,()->gate.publishCooldown(new CooldownRequest("one","other",first.request().until())));
  }
  @Test void acceptedPendingSurvivesNewGateAndBlocksIndependentAdmissionUntilRecovered() {
    var store=new JdbcCooldownPublications(jdbc,manager,clock,JdbcQuotaLockPolicy.DEFAULT,JdbcInsertPolicy.DEFAULT,new CooldownRetry(()->0,CooldownRetry::sleep));
    var pending=store.accept(request("restart",30));assertEquals(CooldownState.DURABLE_PENDING,pending.state());
    assertEquals(0L,jdbc.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group='owner'",Long.class));
    var another=create();assertFalse(another.acquire("owner").granted());assertTrue(another.acquire("other").granted());
    assertEquals("APPLIED",jdbc.queryForObject("select publication_status from MS_COOLDOWN where operation_id='restart'",String.class));
    assertEquals(pending.request().until(),another.nextAdmission("owner").orElseThrow());assertEquals(0,another.recoverCooldowns("owner",1));
  }
  @Test void explicitRecoveryDoesNotConsumeAdmissionOrRequireOwner() {
    var store=new JdbcCooldownPublications(jdbc,manager,clock,JdbcQuotaLockPolicy.DEFAULT,JdbcInsertPolicy.DEFAULT,new CooldownRetry(()->0,CooldownRetry::sleep));
    store.accept(request("pending",30));assertEquals(1,gate.recoverCooldowns("owner",1));
    assertEquals(request("pending",30).until(),gate.acquire("owner").retryAt());
    assertThrows(IllegalArgumentException.class,()->gate.recoverCooldowns("owner",65));
  }
  @Test void completionErrorIsUncertainNotRetriedAsCallbackRefusal() {
    var fault=new CooldownCommitFaultManager(jdbc.getDataSource());
    var quota=new JdbcQuotaGate(jdbc,fault,clock,JdbcQuotaLockPolicy.DEFAULT,JdbcInsertPolicy.DEFAULT,JdbcCooldownPolicy.DURABLE);
    assertThrows(org.springframework.transaction.TransactionSystemException.class,()->quota.publishCooldown(request("uncertain",30)));
    assertFalse(fault.once);assertEquals(1,jdbc.queryForObject("select count(*) from MS_COOLDOWN",Integer.class));
    assertEquals("PENDING",jdbc.queryForObject("select publication_status from MS_COOLDOWN",String.class));
    assertFalse(create().acquire("owner").granted());
  }
  @Test void arbitraryCancellationAfterCommitIsUncertainNotPendingSuccess() {
    var fault=new CooldownCommitFaultManager(jdbc.getDataSource());fault.failure=new java.util.concurrent.CancellationException("synthetic ACK loss");
    var quota=new JdbcQuotaGate(jdbc,fault,clock,JdbcQuotaLockPolicy.DEFAULT,JdbcInsertPolicy.DEFAULT,JdbcCooldownPolicy.DURABLE);
    assertThrows(org.springframework.transaction.TransactionSystemException.class,()->quota.publishCooldown(request("cancel-uncertain",30)));
    assertEquals("PENDING",jdbc.queryForObject("select publication_status from MS_COOLDOWN",String.class));
    assertFalse(create().acquire("owner").granted());
  }
  @Test void durablePublicationCannotSleepUnderCallerTransactionAndLegacyNeedsNoExtension() {
    assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).execute(s->gate.publishCooldown(request("caller",30))));
    assertEquals(0,jdbc.queryForObject("select count(*) from MS_COOLDOWN",Integer.class));
    jdbc.execute("drop table MS_COOLDOWN");jdbc.update("delete from MS_META where meta_name='cooldown_schema_version'");
    var direct=new JdbcQuotaGate(jdbc,manager,clock);assertFalse(direct.supportsDurableCooldowns());
    assertFalse(direct.publishCooldown(request("legacy",30)).durable());assertThrows(RuntimeException.class,this::create);
  }
  @Test void committedCooldownSurvivesFailedWorkMetadataAndDuplicateDoesNotDriftOrRefetch() throws Exception {
    var moving=new io.modelsync.testkit.MutableClock(clock.instant());
    var quota=new JdbcQuotaGate(jdbc,manager,moving,JdbcQuotaLockPolicy.DEFAULT,JdbcInsertPolicy.DEFAULT,JdbcCooldownPolicy.DURABLE);
    var durable=new JdbcWorkStore(jdbc);var store=new AccountingFaultStore(durable);var tx=new io.modelsync.spring.SpringLocalTransactions(manager);
    var projection=new io.modelsync.testkit.MemoryProjectionStore<String>();var calls=new java.util.concurrent.atomic.AtomicInteger();
    var original=new io.modelsync.core.RateLimitedAfter(Duration.ofSeconds(30));
    var model=io.modelsync.core.application.ModelBinding.builder("metadata",String.class).boundary("synthetic")
        .owner("local",r->{calls.incrementAndGet();throw original;}).replica(projection).quota("owner").fingerprint(io.modelsync.core.domain.Digests::sha256Fields).build();
    var c=new io.modelsync.core.application.SyncCoordinator(new io.modelsync.core.application.ModelRegistry(List.of(model)),store,tx,quota,moving,
        io.modelsync.core.SyncOptions.defaults(),io.modelsync.core.WeightedPriorities.defaults(),()->.5);
    var key=new io.modelsync.core.domain.WorkKey("metadata",new io.modelsync.core.domain.SubjectKey("synthetic","one"));
    tx.required(()->store.enqueue(key,io.modelsync.core.Priority.NORMAL,true,moving.instant()));var lease=c.claim().orElseThrow();var plan=c.prepare(lease).orElseThrow();
    assertThrows(io.modelsync.core.RateLimitedAfter.class,()->((BlockingOwner<?>)model.owner()).fetch(plan.request()));
    store.metadataFailure=new LocalContention("work metadata",null);assertThrows(LocalContention.class,()->c.failed(lease,original));
    var expected=moving.instant().plusSeconds(30);
    assertEquals("APPLIED",jdbc.queryForObject("select publication_status from MS_COOLDOWN",String.class));
    assertEquals(expected.toEpochMilli(),jdbc.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group='owner'",Long.class));
    assertEquals("LEASED",jdbc.queryForObject("select work_status from MS_WORK where work_id=?",String.class,key.storageId()));
    moving.advance(Duration.ofSeconds(10));store.metadataFailure=null;c.failed(lease,original);
    assertEquals(expected.toEpochMilli(),jdbc.queryForObject("select next_at_ms from MS_WORK where work_id=?",Long.class,key.storageId()));
    assertEquals(1,calls.get());assertEquals(1,jdbc.queryForObject("select count(*) from MS_COOLDOWN",Integer.class));
  }
}
