package io.modelsync.sample;

import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;

/** Test-only lifecycle handshake and read-only session observation. Never changes submitted SQL. */
final class CreationTransactionProbe implements TransactionExecutionListener {
  final JdbcTemplate jdbc, view;
  final String name;
  final CountDownLatch inserted = new CountDownLatch(1), resolve = new CountDownLatch(1);
  final Map<String, Object> events = new ConcurrentHashMap<>();
  volatile int sid, serial;
  volatile boolean pause, rollback, resolved;
  Runnable verifyInserted;
  CreationTransactionProbe(JdbcTemplate jdbc, JdbcTemplate view, String name) { this.jdbc=jdbc; this.view=view; this.name=name; }
  @Override public void afterBegin(TransactionExecution tx, Throwable failure) {
    if (!name.equals(tx.getTransactionName())) return;
    var id=jdbc.queryForMap("select sid,serial# from v$session where sid=to_number(sys_context('USERENV','SID'))");
    sid=((Number)id.get("SID")).intValue(); serial=((Number)id.get("SERIAL#")).intValue();
    events.put("sid",sid); events.put("serial",serial);
  }
  @Override public void beforeCommit(TransactionExecution tx) {
    if (!name.equals(tx.getTransactionName()) || !pause) return;
    if (verifyInserted != null) verifyInserted.run();
    events.put("after_insert_before_commit", snapshot()); inserted.countDown();
    try { if (!resolve.await(20,TimeUnit.SECONDS)) throw new IllegalStateException("Owned commit handshake expired"); }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Owned handshake interrupted",e); }
    if (rollback) throw new CreationRollback();
  }
  @Override public void beforeRollback(TransactionExecution tx) {
    if (!name.equals(tx.getTransactionName())) return;
    var state=snapshot(); events.put("server_before_rollback",state);
    Object id=state.get("PREV_SQL_ID");
    if (id != null) events.put("previous_sql",view.queryForObject("select sql_fulltext from v$sql where sql_id=? and rownum=1",String.class,id));
  }
  @Override public void afterRollback(TransactionExecution tx, Throwable failure) {
    if (!name.equals(tx.getTransactionName())) return;
    events.put("completion",failure==null?"ROLLED_BACK":"ROLLBACK_FAILED"); events.put("server_after_transaction",snapshot());
  }
  @Override public void afterCommit(TransactionExecution tx, Throwable failure) {
    if (!name.equals(tx.getTransactionName())) return;
    events.put("completion",failure==null?"COMMITTED":"COMMIT_UNCERTAIN"); events.put("server_after_transaction",snapshot());
  }
  Map<String,Object> snapshot() {
    var state=new LinkedHashMap<>(view.queryForMap("select status,event,state,sql_id,prev_sql_id,blocking_session,"
        + "case when taddr is null then 0 else 1 end TRANSACTION_ACTIVE,"
        + "case when event='enq: TX - row lock contention' and state='WAITING' then 1 else 0 end LOCK_WAIT"
        + " from v$session where sid=? and serial#=?",sid,serial));
    state.put("SID",sid);state.put("SERIAL",serial);return state;
  }
  void decide(boolean abort) { rollback=abort;resolved=true;resolve.countDown(); }
}
