package io.modelsync.testkit;

import io.modelsync.core.WorkLease;
import io.modelsync.core.Priority;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.WorkStore;
import java.time.*;
import java.util.*;

/** Records the public dispatch capability selected by the coordinator, delegating real fixture work. */
final class ClaimDispatchWorkStore implements WorkStore {
  private final WorkStore delegate;
  final List<Set<String>> filteredBindings = new ArrayList<>();
  int unrestrictedClaims;

  ClaimDispatchWorkStore(WorkStore delegate) { this.delegate = delegate; }
  public String enqueue(WorkKey k, Priority p, boolean f, Instant n) { return delegate.enqueue(k,p,f,n); }
  public Optional<WorkLease> claim(Priority p, Instant n, Duration d) {
    unrestrictedClaims++;
    return delegate.claim(p,n,d);
  }
  public Optional<WorkLease> claim(Priority p, Instant n, Duration d, Set<String> bindings) {
    filteredBindings.add(Set.copyOf(bindings));
    return delegate.claim(p,n,d,bindings);
  }
  public boolean lockValid(WorkLease l, Instant n) { return delegate.lockValid(l,n); }
  public SyncState state(WorkKey k) { return delegate.state(k); }
  public void complete(WorkLease l, SyncState s, Instant next, Instant n) { delegate.complete(l,s,next,n); }
  public void reschedule(WorkLease l, Instant next, String c, boolean f, int m, Instant n) { delegate.reschedule(l,next,c,f,m,n); }
}
