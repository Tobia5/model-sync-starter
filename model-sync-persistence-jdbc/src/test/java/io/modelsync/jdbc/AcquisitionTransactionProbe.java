package io.modelsync.jdbc;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;

/** Test-only exact-session observations; leaves the real quota SQL and transaction manager intact. */
final class AcquisitionTransactionProbe implements TransactionExecutionListener {
  final JdbcTemplate runtime, observer;
  final String guard;
  final List<Map<String,Object>> transactions = new ArrayList<>();
  boolean armed;
  private Map<String,Object> active;
  private int sid, serial;
  private long start;
  AcquisitionTransactionProbe(JdbcTemplate runtime, JdbcTemplate observer, String guard) {
    this.runtime=runtime;this.observer=observer;this.guard=guard;
  }
  @Override public void afterBegin(TransactionExecution tx, Throwable failure) {
    if (!armed || !"modelSyncQuota".equals(tx.getTransactionName())) return;
    active=new LinkedHashMap<>();transactions.add(active);start=System.nanoTime();
    var id=runtime.queryForMap("select sid,serial# from v$session where sid=to_number(sys_context('USERENV','SID'))");
    sid=((Number)id.get("SID")).intValue();serial=((Number)id.get("SERIAL#")).intValue();
    runtime.update("update MS_QUOTA set next_allowed_ms=123 where quota_group=?",guard);
  }
  @Override public void beforeRollback(TransactionExecution tx) {
    if(active==null || !"modelSyncQuota".equals(tx.getTransactionName()))return;
    var before=snapshot();active.put("server_before_rollback",before);
    active.put("before_rollback_ms",(System.nanoTime()-start)/1_000_000);
    String sql=observer.queryForObject("select sql_fulltext from v$sql where sql_id=? and rownum=1",String.class,before.get("PREV_SQL_ID"));
    if(sql==null || sql.length()>2048 || !sql.toLowerCase(Locale.ROOT).endsWith("nowait"))
      throw new IllegalStateException("Expected bounded quota SQL not observed");
    active.put("actual_sql",sql);
  }
  @Override public void afterRollback(TransactionExecution tx, Throwable failure) {
    if(active==null || !"modelSyncQuota".equals(tx.getTransactionName()))return;
    active.put("server_after_rollback",snapshot());active.put("after_rollback_ms",(System.nanoTime()-start)/1_000_000);
    active.put("completion",failure==null?"ROLLED_BACK":"ROLLBACK_FAILED");active=null;
  }
  @Override public void afterCommit(TransactionExecution tx, Throwable failure) {
    if(active==null || !"modelSyncQuota".equals(tx.getTransactionName()))return;
    active.put("completion",failure==null?"COMMITTED":"COMMIT_UNCERTAIN");active=null;
  }
  private Map<String,Object> snapshot() {
    return observer.queryForMap("select sid,serial#,status,event,state,sql_id,prev_sql_id,"
        +"case when taddr is null then 0 else 1 end TRANSACTION_ACTIVE,"
        +"case when event='enq: TX - row lock contention' and state='WAITING' then 1 else 0 end LOCK_WAIT"
        +" from v$session where sid=? and serial#=?",sid,serial);
  }
}
