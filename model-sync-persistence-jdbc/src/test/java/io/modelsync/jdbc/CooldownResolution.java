package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** A separately measured bounded drain; a PENDING return is never counted as an APPLIED return. */
final class CooldownResolution {
  private CooldownResolution() {}
  static Map<String,Object> resolve(JdbcQuotaGate gate,JdbcTemplate jdbc,String group,MeasuredQuotaGate measured) {
    var result=new LinkedHashMap<String,Object>();long start=System.nanoTime();
    var before=OracleMeasurements.snapshot(jdbc);
    int recovered=gate.recoverCooldowns(group,64);
    result.put("recovered_in_drain",recovered);result.put("drain_ms",(System.nanoTime()-start)/1_000_000);
    result.put("drain_work",OracleMeasurements.difference(before,OracleMeasurements.snapshot(jdbc)));
    var rows=jdbc.queryForList("select operation_id,deadline_ms,publication_status from MS_COOLDOWN where quota_group=? order by operation_id",group);
    result.put("receipts",measured.publicationFacts());result.put("persisted_rows",rows);
    long pending=rows.stream().filter(r->!"APPLIED".equals(r.get("PUBLICATION_STATUS"))).count();
    result.put("unresolved",pending);result.put("status",pending==0?"PASS":"FAILED");
    return result;
  }
  static void check(Map<String,Object> result,MeasuredQuotaGate measured) {
    assertEquals("PASS",result.get("status"));assertEquals(0L,result.get("unresolved"));
    assertEquals(0,measured.cooldownFailures.get());
    var events=(List<Map<String,Object>>)result.get("receipts");var rows=(List<Map<String,Object>>)result.get("persisted_rows");
    assertEquals(measured.cooldowns.get(),events.size());assertEquals(measured.cooldowns.get(),measured.durable.get());
    assertEquals(measured.cooldowns.get(),measured.published.get()+measured.pending.get());
    var unique=new HashMap<String,Long>();
    for(var e:events){assertEquals(true,e.get("durable"));assertTrue(Set.of("APPLIED","DURABLE_PENDING").contains(e.get("state")));
      long deadline=((Number)e.get("canonical_deadline_ms")).longValue();
      var previous=unique.putIfAbsent((String)e.get("operation_id"),deadline);if(previous!=null)assertEquals(previous.longValue(),deadline);}
    assertEquals(unique.size(),rows.size());
    for(var row:rows){assertEquals("APPLIED",row.get("PUBLICATION_STATUS"));assertEquals(unique.get(row.get("OPERATION_ID")),((Number)row.get("DEADLINE_MS")).longValue());}
  }
  static Map<String,Object> physical(CooldownObservation observer,Map<String,Long> before) {
    var after=observer.snapshot();var delta=new LinkedHashMap<String,Long>();
    after.forEach((k,v)->delta.put(k,k.endsWith("active")?v:v-before.get(k)));
    return Map.of("before",before,"after",after,"delta",delta,"detached_sql_failures",observer.failures());
  }
  static void physicalCheck(Map<String,Object> evidence) {
    var delta=(Map<String,Long>)evidence.get("delta");
    var after=(Map<String,Long>)evidence.get("after");var failures=(List<Map<String,Object>>)evidence.get("detached_sql_failures");
    long facts=failures.stream().mapToLong(f->((Number)f.get("count")).longValue()).sum();
    assertEquals(after.get("accept_refusals")+after.get("apply_refusals")+after.get("accept_other_sql_failures")+after.get("apply_other_sql_failures"),facts);
    for(String p:List.of("accept","apply")) {
      assertEquals(0L,delta.get(p+"_active"));assertEquals(0L,delta.get(p+"_completion_failures"));assertEquals(0L,delta.get(p+"_other_sql_failures"));
      assertEquals(delta.get(p+"_attempts"),delta.get(p+"_commits")+delta.get(p+"_rollbacks"));
      assertEquals(delta.get(p+"_rollbacks"),delta.get(p+"_refusals"));
    }
  }
}
