package io.modelsync.jdbc;

import io.modelsync.core.*;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.WorkStore;
import java.time.*;
import java.util.*;

/** Observes actual calls; does not replace SQL, fencing or transaction semantics. */
final class ObservedWorkStore extends ForwardingWorkStore {
  private final ObservedLocalTransactions tx;
  ObservedWorkStore(WorkStore delegate, ObservedLocalTransactions tx) { super(delegate); this.tx = tx; }
  @Override public Optional<WorkLease> claim(Priority p, Instant n, Duration d) { return claim(p,n,d,null); }
  @Override public Optional<WorkLease> claim(Priority p, Instant n, Duration d, Set<String> b) {
    var frame = tx.transaction(); frame.claimStarted = true;
    var result = b == null ? delegate.claim(p,n,d) : delegate.claim(p,n,d,b);
    if (result.isPresent()) {
      if (frame.claimReturned) throw new IllegalStateException("Multiple claims in one fixture transaction");
      frame.claimReturned = true;
    }
    return result;
  }
  @Override public boolean lockValid(WorkLease lease, Instant now) {
    boolean valid = delegate.lockValid(lease, now);
    if (!valid) tx.transaction().fenceRejections++;
    return valid;
  }
  @Override public void complete(WorkLease l, SyncState s, Instant next, Instant n) {
    delegate.complete(l,s,next,n); tx.transaction().completions++;
  }
  @Override public void completeOnDemand(WorkLease l, SyncState s, Instant n) {
    delegate.completeOnDemand(l,s,n); tx.transaction().completions++;
  }
  @Override public void reschedule(WorkLease l, Instant next, String code, boolean f, int max, Instant n) {
    tx.transaction().rescheduleStarted = true;
    delegate.reschedule(l,next,code,f,max,n); tx.transaction().reschedules++;
    if ("LOCAL_CONFLICT".equals(code)) tx.transaction().localConflicts++;
  }
}
