package io.modelsync.spring;

import io.modelsync.core.*;
import io.modelsync.testkit.CoreFixture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

/** Framework lane; memory transactions prove intent flow, not JPA/JDBC enlistment. */
class SyncEventListenerTest {
  @Test void listenerEnqueuesSynchronouslyEvenWhenMulticasterHasExecutor() {
    var f = new CoreFixture();
    var listener = new SyncEventListener(new SyncTriggers(f.gateway));
    var multicaster = new SimpleApplicationEventMulticaster();
    var asyncCalls = new AtomicInteger();
    multicaster.setTaskExecutor(runnable -> { asyncCalls.incrementAndGet(); runnable.run(); });
    multicaster.addApplicationListener(listener);
    multicaster.multicastEvent(new PayloadApplicationEvent<>(this, SyncRequested.changed("customer", "tenant-a", "42")));
    assertEquals(0, asyncCalls.get());
    assertEquals(1, f.store.size());
    assertEquals(0, f.calls.get());
  }
  @Test void unrelatedPayloadIsIgnored() {
    var f = new CoreFixture();
    var multicaster = new SimpleApplicationEventMulticaster();
    multicaster.addApplicationListener(new SyncEventListener(new SyncTriggers(f.gateway)));
    multicaster.multicastEvent(new PayloadApplicationEvent<>(this, "not a sync request"));
    assertEquals(0, f.store.size());
  }
  @Test void readOnlyTransactionFailsRatherThanLosingRequest() {
    var f = new CoreFixture();
    var listener = new SyncEventListener(new SyncTriggers(f.gateway));
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
    try {
      assertThrows(IllegalStateException.class, () -> listener.onApplicationEvent(
          new PayloadApplicationEvent<>(this, SyncRequested.changed("customer", "tenant-a", "42"))));
      assertEquals(0, f.store.size());
    } finally { TransactionSynchronizationManager.setCurrentTransactionReadOnly(false); }
  }
}
