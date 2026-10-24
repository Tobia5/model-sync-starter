package io.modelsync.sample;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;

/** Fixture-only transaction observations; SQL and transaction behavior are never replaced. */
final class QuotaTransactionProbe implements TransactionExecutionListener {
  final JdbcTemplate jdbc, view;
  final List<Map<String, Object>> transactions = new ArrayList<>();
  boolean armed;
  String guard;
  private Map<String, Object> active;
  private int sid, serial;
  private long started;
  QuotaTransactionProbe(JdbcTemplate jdbc, JdbcTemplate view) { this.jdbc = jdbc; this.view = view; }
  @Override public void afterBegin(TransactionExecution tx, Throwable failure) {
    if (!armed || !"modelSyncQuota".equals(tx.getTransactionName())) return;
    active = new LinkedHashMap<>(); transactions.add(active);
    started = System.nanoTime();
    var id = jdbc.queryForMap("select sid,serial# from v$session where sid=to_number(sys_context('USERENV','SID'))");
    sid = ((Number) id.get("SID")).intValue(); serial = ((Number) id.get("SERIAL#")).intValue();
    active.put("sid", sid); active.put("serial", serial); active.put("begin_failed", failure != null);
    if (guard != null) jdbc.update("update MS_QUOTA set next_allowed_ms=123 where quota_group=?", guard);
  }
  @Override public void beforeRollback(TransactionExecution tx) {
    if (active == null || !"modelSyncQuota".equals(tx.getTransactionName())) return;
    var before = snapshot(sid, serial); active.put("server_before_rollback", before);
    active.put("before_rollback_elapsed_ms", java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    active.put("server_sql", view.queryForObject("select sql_fulltext from v$sql where sql_id=? and rownum=1", String.class, before.get("PREV_SQL_ID")));
  }
  @Override public void afterRollback(TransactionExecution tx, Throwable failure) {
    if (active == null || !"modelSyncQuota".equals(tx.getTransactionName())) return;
    active.put("server_after_rollback", snapshot(sid, serial));
    active.put("after_rollback_elapsed_ms", java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    active.put("completion", failure == null ? "ROLLED_BACK" : "ROLLBACK_FAILED"); active = null;
  }
  @Override public void afterCommit(TransactionExecution tx, Throwable failure) {
    if (active == null || !"modelSyncQuota".equals(tx.getTransactionName())) return;
    active.put("completion", failure == null ? "COMMITTED" : "COMMIT_UNCERTAIN"); active = null;
  }
  Map<String, Object> snapshot(int id, int sequence) {
    var values = new LinkedHashMap<>(view.queryForMap("select status,event,state,sql_id,prev_sql_id,"
        + "case when taddr is null then 0 else 1 end TRANSACTION_ACTIVE,"
        + "case when event='enq: TX - row lock contention' and state='WAITING' then 1 else 0 end LOCK_WAIT"
        + " from v$session where sid=? and serial#=?", id, sequence));
    values.put("SID", id); values.put("SERIAL", sequence); return values;
  }
}
