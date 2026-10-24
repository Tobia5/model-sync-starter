package io.modelsync.jdbc;

import io.modelsync.core.*;
import io.modelsync.core.port.WorkStore;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

final class CountedWorkStore extends ForwardingWorkStore {
  final AtomicLong selections = new AtomicLong();
  final AtomicLong empty = new AtomicLong();
  CountedWorkStore(WorkStore delegate) { super(delegate); }
  @Override public Optional<WorkLease> claim(Priority p, Instant n, Duration d) { return count(delegate.claim(p,n,d)); }
  @Override public Optional<WorkLease> claim(Priority p, Instant n, Duration d, Set<String> b) { return count(delegate.claim(p,n,d,b)); }
  private Optional<WorkLease> count(Optional<WorkLease> result) {
    selections.incrementAndGet();
    if(result.isEmpty())empty.incrementAndGet();
    return result;
  }
}
