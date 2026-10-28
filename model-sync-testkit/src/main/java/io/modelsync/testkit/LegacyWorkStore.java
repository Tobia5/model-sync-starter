package io.modelsync.testkit;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public record LegacyWorkStore(WorkStore delegate) implements WorkStore {
  public String enqueue(WorkKey k, Priority p, boolean f, Instant n) { return delegate.enqueue(k,p,f,n); }
  public Optional<WorkLease> claim(Priority p, Instant n, Duration d) { return delegate.claim(p,n,d); }
  public boolean lockValid(WorkLease l, Instant n) { return delegate.lockValid(l,n); }
  public SyncState state(WorkKey k) { return delegate.state(k); }
  public void complete(WorkLease l, SyncState s, Instant next, Instant n) { delegate.complete(l,s,next,n); }
  public void reschedule(WorkLease l, Instant next, String c, boolean f, int m, Instant n) { delegate.reschedule(l,next,c,f,m,n); }
}
