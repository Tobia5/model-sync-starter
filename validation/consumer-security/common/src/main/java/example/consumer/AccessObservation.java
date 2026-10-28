package example.consumer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class AccessObservation {
  public final AtomicInteger reads = new AtomicInteger();
  public final AtomicInteger enqueues = new AtomicInteger();
  public final AtomicInteger events = new AtomicInteger();
  public final List<AuthorizedInvocation> accepted = new CopyOnWriteArrayList<>();
  public synchronized void record(String principal, String operation, String tenant, String binding, String subject) {
    if (accepted.size() >= 128) throw new IllegalStateException("Bounded security observation exhausted");
    accepted.add(new AuthorizedInvocation(principal, operation, tenant, binding, subject, Thread.currentThread().getName()));
  }
  public AccessCounts counts() { return new AccessCounts(reads.get(), enqueues.get(), events.get()); }
}
