package io.modelsync.jdbc;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.transaction.*;

/** Test-only physical transaction and detached SQL observations, independent of logical receipts. */
final class CooldownObservation implements TransactionExecutionListener {
  private final ThreadLocal<String> operation=new ThreadLocal<>();
  private final Map<String,AtomicLong> counts=new LinkedHashMap<>();
  private final Map<DetachedFailure,Long> failures=new LinkedHashMap<>();
  CooldownObservation() {
    for(String phase:List.of("accept","apply")) for(String outcome:List.of("attempts","active","commits","rollbacks","completion_failures","refusals","other_sql_failures"))
      counts.put(phase+"_"+outcome,new AtomicLong());
  }
  private String phase(TransactionExecution tx) {
    return "modelSyncCooldownAccept".equals(tx.getTransactionName())?"accept":
        "modelSyncCooldownApply".equals(tx.getTransactionName())?"apply":null;
  }
  private void add(String p,String outcome) {counts.get(p+"_"+outcome).incrementAndGet();}
  @Override public void afterBegin(TransactionExecution tx,Throwable failure) {
    String p=phase(tx);if(p==null)return;
    add(p,"attempts");if(failure!=null){add(p,"completion_failures");return;}
    if(operation.get()!=null)throw new IllegalStateException("Nested publication transaction");
    operation.set(p);add(p,"active");
  }
  @Override public void afterCommit(TransactionExecution tx,Throwable failure) {finish(tx,failure,"commits");}
  @Override public void afterRollback(TransactionExecution tx,Throwable failure) {finish(tx,failure,"rollbacks");}
  private void finish(TransactionExecution tx,Throwable failure,String outcome) {
    String p=phase(tx);if(p==null || operation.get()==null)return;
    add(p,failure==null?outcome:"completion_failures");counts.get(p+"_active").decrementAndGet();operation.remove();
  }
  void sqlFailure(Throwable error) {
    String p=operation.get();if(p==null)return;
    var fact=DetachedFailure.capture(error);
    add(p,Integer.valueOf(54).equals(fact.vendorCode())?"refusals":"other_sql_failures");
    synchronized(this){if(failures.size()>=32&&!failures.containsKey(fact))throw new IllegalStateException("Detached publication fact bound");failures.merge(fact,1L,Long::sum);}
  }
  Map<String,Long> snapshot() {var v=new LinkedHashMap<String,Long>();counts.forEach((k,c)->v.put(k,c.get()));return v;}
  synchronized List<Map<String,Object>> failures() {
    return failures.entrySet().stream().map(e->{var v=new LinkedHashMap<String,Object>(e.getKey().facts());v.put("count",e.getValue());return (Map<String,Object>)v;}).toList();
  }
}
