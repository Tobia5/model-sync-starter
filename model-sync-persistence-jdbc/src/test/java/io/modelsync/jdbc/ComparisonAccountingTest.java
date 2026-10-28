package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.*;
import io.modelsync.core.port.*;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ComparisonAccountingTest {
  private static LocalContention refusal() { return new LocalContention("test", new SQLException("DO_NOT_EXPORT", "61000", 54)); }
  private static Map<?, ?> only(Map<String,Object> report) { return (Map<?,?>)((List<?>)report.get("attempts")).getFirst(); }
  private static long count(Map<String,Object> r, String key) { return ((Number)((Map<?,?>)r.get("totals")).get(key)).longValue(); }

  @Test void actualDetachedExportMatchesTheSharedPythonSchemaFixture() throws Exception {
    var expected=JsonMapper.builder().build().readValue(java.nio.file.Files.readString(
        java.nio.file.Path.of("../scripts/tests/fixtures/acquisition-refusal.json")),Map.class);
    assertEquals(expected,DetachedFailure.capture(refusal()).facts());
  }
  @Test void grantIsCountedAfterCommitAndSeparatelyFromCommittedReconciliation() {
    var f = new ComparisonTestFixture(); assertTrue(f.accounting.runOne(f.coordinator));
    var r=f.finish(); ComparisonAccountingValidator.check(r,f.physical.get(),1);
    assertEquals("RECONCILED",only(r).get("terminal")); assertEquals(1,count(r,"acquisitions"));
  }
  @Test void denialIsReturnedDecisionNotAnAcquisitionFailure() {
    var f=new ComparisonTestFixture(); f.scripted.admission=()->QuotaAdmission.waitUntil(f.clock.instant().plusSeconds(5));
    assertTrue(f.accounting.runOne(f.coordinator)); var r=f.finish(); ComparisonAccountingValidator.check(r,0,0);
    assertEquals("DENIED",only(r).get("terminal")); assertEquals(0,count(r,"acquisition_failures"));
  }
  @Test void localContentionIsCapturedBeforeRunOneHidesItAndHasNoOwnerCall() {
    var f=new ComparisonTestFixture();f.scripted.admission=()->{throw refusal();};
    assertTrue(f.accounting.runOne(f.coordinator));var r=f.finish();ComparisonAccountingValidator.check(r,0,0);
    assertEquals("ACQUISITION_FAILED",only(r).get("terminal"));assertEquals(1,count(r,"acquisition_failures"));
    assertEquals(1,f.quota.failures.get());assertFalse(JsonMapper.builder().build().writeValueAsString(r).contains("DO_NOT_EXPORT"));
  }
  @Test void unknownAcquisitionFailureIsRecordedAndCannotQualify() {
    var f=new ComparisonTestFixture();f.scripted.admission=()->{throw new IllegalStateException("DO_NOT_EXPORT");};
    assertTrue(f.accounting.runOne(f.coordinator));var r=f.finish();
    assertEquals(1,count(r,"acquisition_failures"));assertThrows(IllegalStateException.class,()->ComparisonAccountingValidator.check(r,0,0));
  }
  @Test void uncertainAcquisitionCommitIsNeverAContentionOrGrant() {
    var f=new ComparisonTestFixture();f.scripted.admission=()->{throw new org.springframework.transaction.TransactionSystemException("DO_NOT_EXPORT",new SQLException("DO_NOT_EXPORT","08007",17002));};
    assertTrue(f.accounting.runOne(f.coordinator));var r=f.finish();
    assertEquals("UNCERTAIN_COMMIT",((Map<?,?>)((Map<?,?>)only(r).get("faults")).get("ACQUIRE")).get("category"));
    assertEquals(0,count(r,"grants"));assertThrows(IllegalStateException.class,()->ComparisonAccountingValidator.check(r,0,0));
  }
  @Test void preparationFailureIsNotManufacturedAsAnAcquisitionFailure() {
    var f=new ComparisonTestFixture();f.faults.preparationFailure=refusal();
    assertTrue(f.accounting.runOne(f.coordinator));var r=f.finish();ComparisonAccountingValidator.check(r,0,0);
    assertEquals("PRE_ADMISSION_FAILED",only(r).get("terminal"));assertEquals(0,count(r,"acquisitions"));
  }
  @Test void freshNoFetchCompletesWithoutSpendingAdmissionOrRenewingOwnerEvidence() {
    var f=new ComparisonTestFixture();assertTrue(f.accounting.runOne(f.coordinator));f.finish();
    var validated=f.durable.state(f.key).validatedAt();f.enqueue();f.accounting.begin("measurement",f.quota);
    assertTrue(f.accounting.runOne(f.coordinator));var r=f.finish();ComparisonAccountingValidator.check(r,0,0);
    assertEquals("FRESH_NO_FETCH",only(r).get("terminal"));assertEquals(1,count(r,"completions"));
    assertEquals(0,count(r,"committed_reconciliations"));assertEquals(validated,f.durable.state(f.key).validatedAt());
  }
  @Test void postGrantOwnerFailureIsNotAnAcquisitionFailure() {
    var f=new ComparisonTestFixture(request->{throw new SocketTimeoutException("DO_NOT_EXPORT");});
    assertTrue(f.accounting.runOne(f.coordinator));var r=f.finish();ComparisonAccountingValidator.check(r,1,0);
    assertEquals("OWNER_FAILED",only(r).get("terminal"));assertEquals(0,count(r,"acquisition_failures"));
  }
  @Test void failedRetryPersistenceIsSeparateAndCannotQualify() {
    var f=new ComparisonTestFixture(request->{throw new SocketTimeoutException("DO_NOT_EXPORT");});f.faults.metadataFailure=refusal();
    assertThrows(LocalContention.class,()->f.accounting.runOne(f.coordinator));var r=f.finish();
    assertEquals("FAILURE_HANDLING_FAILED",only(r).get("terminal"));assertEquals(0,count(r,"reschedules"));
    assertEquals(0,count(r,"acquisition_failures"));assertTrue(((Map<?,?>)only(r).get("faults")).containsKey("FAILURE_METADATA"));
    assertThrows(IllegalStateException.class,()->ComparisonAccountingValidator.check(r,1,0));
  }
  @Test void resultFailureAfterGrantIsSeparateAndProjectionRollsBack() {
    var f=new ComparisonTestFixture();f.faults.resultFailure=refusal();
    assertTrue(f.accounting.runOne(f.coordinator));var r=f.finish();ComparisonAccountingValidator.check(r,1,0);
    assertEquals("RESULT_FAILED",only(r).get("terminal"));assertEquals(0,count(r,"acquisition_failures"));
    assertEquals(1,count(r,"grants"));assertEquals(0,count(r,"completions"));
  }
  @Test void successfulCooldownIsPublishedOnlyAfterBoundaryReturns() {
    var f=new ComparisonTestFixture(request->{throw new RateLimitedAfter(Duration.ofSeconds(1));});
    assertTrue(f.accounting.runOne(f.coordinator));var r=f.finish();ComparisonAccountingValidator.check(r,1,0);
    assertEquals(1,count(r,"cooldown_attempts"));assertEquals(1,count(r,"cooldown_published"));
    assertEquals(0,count(r,"cooldown_failures"));
  }
  @Test void knownNonContentionAcquisitionFailureStillCannotQualify() {
    var f=new ComparisonTestFixture();f.scripted.admission=()->{throw new org.springframework.jdbc.UncategorizedSQLException("fixture","redacted",new SQLException("DO_NOT_EXPORT","23000",1));};
    assertTrue(f.accounting.runOne(f.coordinator));var r=f.finish();assertEquals(1,f.quota.knownFailures.get());
    assertEquals(0,f.quota.refusals.get());assertThrows(IllegalStateException.class,()->ComparisonAccountingValidator.check(r,0,0));
  }
  @Test void cooldownCountsAttemptPublicationAndFailureIndependently() {
    var f=new ComparisonTestFixture(request->{throw new RateLimitedAfter(Duration.ofSeconds(1));});
    f.scripted.publication=until->{throw refusal();};assertThrows(LocalContention.class,()->f.accounting.runOne(f.coordinator));
    var r=f.finish();assertEquals(1,count(r,"cooldown_attempts"));assertEquals(0,count(r,"cooldown_published"));
    assertEquals(1,count(r,"cooldown_failures"));assertEquals(0,count(r,"acquisition_failures"));
    assertThrows(IllegalStateException.class,()->ComparisonAccountingValidator.check(r,1,0));
  }
  @Test void warmupMeasurementAndDrainCannotMixActiveAttempts() throws Exception {
    var f=new ComparisonTestFixture();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    f.scripted.admission=()->{entered.countDown();try {if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("fixture bound");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return QuotaAdmission.allow();};
    try(var executor=Executors.newSingleThreadExecutor()) {
      var work=executor.submit(()->f.accounting.runOne(f.coordinator));assertTrue(entered.await(5,TimeUnit.SECONDS));
      assertEquals(1,f.quota.active.get());assertThrows(IllegalStateException.class,()->f.accounting.begin("warmup",f.quota));
      f.accounting.stopAdmissions();assertTrue(f.accounting.runIf(f.coordinator,()->true).isEmpty());release.countDown();assertTrue(work.get(5,TimeUnit.SECONDS));
    } finally {release.countDown();}
    var r=f.finish();ComparisonAccountingValidator.check(r,1,1);assertEquals(1,r.get("completed_during_drain"));
    f.accounting.begin("drain-probe",f.quota);assertFalse(f.accounting.runOne(f.coordinator));var empty=f.finish();ComparisonAccountingValidator.check(empty,0,0);
    assertEquals(0,count(empty,"acquisitions"));
  }
  @Test void concurrentBoundaryCountersAreIndependentAndReturnToZero() throws Exception {
    var entered=new CountDownLatch(4);var release=new CountDownLatch(1);var gate=new ScriptedQuotaGate();
    gate.admission=()->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("fixture bound");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return QuotaAdmission.allow();};
    var measured=new MeasuredQuotaGate(gate);
    try(var pool=Executors.newFixedThreadPool(4)) {
      var futures=new ArrayList<Future<?>>();for(int i=0;i<4;i++)futures.add(pool.submit(()->measured.acquire("synthetic")));
      assertTrue(entered.await(5,TimeUnit.SECONDS));assertEquals(4,measured.acquisitions.get());assertEquals(4,measured.active.get());
      assertEquals(0,measured.granted.get());release.countDown();for(var future:futures)future.get(5,TimeUnit.SECONDS);
    } finally {release.countDown();}
    assertEquals(4,measured.granted.get());assertEquals(0,measured.active.get());assertEquals(0,measured.failures.get());
  }
  @Test void missingEventsDoubleCountingAndIndependentOutcomeMismatchFailValidation() {
    var f=new ComparisonTestFixture();assertTrue(f.accounting.runOne(f.coordinator));var good=f.finish();
    for(String field:List.of("claims","acquisitions","grants","owner_calls","admission_uses","completions")) {
      var bad=copy(good);var attempt=(Map<String,Object>)((List<?>)bad.get("attempts")).getFirst();attempt.put(field,2);
      assertThrows(IllegalStateException.class,()->ComparisonAccountingValidator.check(bad,1,1),field);
    }
    var missing=copy(good);((List<?>)missing.get("attempts")).clear();assertThrows(IllegalStateException.class,()->ComparisonAccountingValidator.check(missing,1,1));
    assertThrows(IllegalStateException.class,()->ComparisonAccountingValidator.check(good,2,1));
    assertThrows(IllegalStateException.class,()->ComparisonAccountingValidator.check(good,1,0));
  }
  @Test void correlationBoundFailsClosedAndFactsNeverRetainExceptionComponents() {
    assertTrue(Arrays.stream(DetachedFailure.class.getRecordComponents()).noneMatch(c->Throwable.class.isAssignableFrom(c.getType())));
    var f=new ComparisonTestFixture();var limited=new ComparisonAccounting(0);limited.begin("measurement",f.quota);
    assertThrows(IllegalStateException.class,()->limited.runOne(f.coordinator));
  }
  @SuppressWarnings("unchecked") private static Map<String,Object> copy(Map<String,Object> value) {
    var mapper=JsonMapper.builder().build();return mapper.readValue(mapper.writeValueAsString(value),Map.class);
  }
}
