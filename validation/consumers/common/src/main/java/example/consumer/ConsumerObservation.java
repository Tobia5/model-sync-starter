package example.consumer;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/** Bounded SQL and thread evidence; does not alter SQL. Synthetic fixture only. */
public final class ConsumerObservation implements StatementInspector {
  public final Set<String> persistenceThreads = ConcurrentHashMap.newKeySet();
  public final Set<String> ownerThreads = ConcurrentHashMap.newKeySet();
  public final Set<String> requestThreads = ConcurrentHashMap.newKeySet();
  public final Set<String> lookupPolicies = ConcurrentHashMap.newKeySet();
  public final List<String> sql = new CopyOnWriteArrayList<>();
  public final AtomicInteger dropped = new AtomicInteger();
  public final AtomicInteger ownerCalls = new AtomicInteger();
  public final AtomicInteger ownerActive = new AtomicInteger();
  @Override public synchronized String inspect(String statement) {
    if (sql.size() < 200) sql.add(statement); else dropped.incrementAndGet(); return statement;
  }
}
