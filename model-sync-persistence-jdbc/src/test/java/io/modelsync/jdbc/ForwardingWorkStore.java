package io.modelsync.jdbc;

import io.modelsync.core.*;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.WorkStore;
import java.time.*;
import java.util.*;

/** Test-only delegation, keeping comparison selectors on the real JDBC ledger/completion path. */
abstract class ForwardingWorkStore implements WorkStore {
  protected final WorkStore delegate;
  ForwardingWorkStore(WorkStore delegate) { this.delegate = delegate; }
  @Override public String enqueue(WorkKey k, Priority p, boolean f, Instant n) { return delegate.enqueue(k,p,f,n); }
  @Override public Optional<String> enqueueRefresh(WorkKey k, Priority p, Instant n) { return delegate.enqueueRefresh(k,p,n); }
  @Override public Optional<WorkLease> claim(Priority p, Instant n, Duration d) { return delegate.claim(p,n,d); }
  @Override public Optional<WorkLease> claim(Priority p, Instant n, Duration d, Set<String> b) { return delegate.claim(p,n,d,b); }
  @Override public boolean lockValid(WorkLease l, Instant n) { return delegate.lockValid(l,n); }
  @Override public SyncState state(WorkKey k) { return delegate.state(k); }
  @Override public void complete(WorkLease l, SyncState s, Instant next, Instant n) { delegate.complete(l,s,next,n); }
  @Override public boolean supportsOnDemand() { return delegate.supportsOnDemand(); }
  @Override public void completeOnDemand(WorkLease l, SyncState s, Instant n) { delegate.completeOnDemand(l,s,n); }
  @Override public void reschedule(WorkLease l, Instant next, String c, boolean f, int max, Instant n) { delegate.reschedule(l,next,c,f,max,n); }
}
