package io.modelsync.jdbc;

import io.modelsync.core.*;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.WorkStore;
import java.time.Instant;

/** Faults are injected at named real coordinator calls, not into its return value. */
final class AccountingFaultStore extends ForwardingWorkStore {
  RuntimeException preparationFailure, metadataFailure, resultFailure;
  AccountingFaultStore(WorkStore delegate) { super(delegate); }
  @Override public SyncState state(WorkKey key) {
    if (preparationFailure != null) throw preparationFailure;
    return delegate.state(key);
  }
  @Override public void reschedule(WorkLease l, Instant n, String c, boolean f, int max, Instant now) {
    if (metadataFailure != null) throw metadataFailure;
    delegate.reschedule(l,n,c,f,max,now);
  }
  @Override public void complete(WorkLease l, SyncState s, Instant next, Instant now) {
    if (resultFailure != null) throw resultFailure;
    delegate.complete(l,s,next,now);
  }
}
