package io.modelsync.sample;

import io.modelsync.core.FetchResult;
import io.modelsync.core.ReadRequest;
import io.modelsync.core.port.BlockingOwner;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Synthetic owner, fixed 5 ms latency, no hidden retries. */
final class WorkerOwner implements BlockingOwner<Customer> {
  final AtomicInteger calls = new AtomicInteger(), active = new AtomicInteger(), maximum = new AtomicInteger();
  public FetchResult<Customer> fetch(ReadRequest request) throws Exception {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new AssertionError("Worker owner called under a database transaction");
    calls.incrementAndGet();
    maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
    try {
      Thread.sleep(5);
      return FetchResult.found(new Customer("synthetic", "ACTIVE"), "worker-v1");
    } finally { active.decrementAndGet(); }
  }
}
