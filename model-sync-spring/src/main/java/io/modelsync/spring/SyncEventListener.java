package io.modelsync.spring;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.SyncRequested;
import io.modelsync.core.application.SyncTriggers;
import java.util.Objects;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Synchronous enqueue adapter, never an AFTER_COMMIT callback or an asynchronous owner call.
 * Publish before local mutation when sharing a transaction, to keep ledger-first lock ordering.
 */
public final class SyncEventListener implements ApplicationListener<PayloadApplicationEvent<SyncRequested>> {
  private final SyncTriggers triggers;

  public SyncEventListener(SyncTriggers triggers) {
    this.triggers = Objects.requireNonNull(triggers, "triggers");
  }

  @Override
  public boolean supportsAsyncExecution() { return false; }

  @Override
  public void onApplicationEvent(PayloadApplicationEvent<SyncRequested> event) {
    if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
      throw new IllegalStateException("A synchronization event cannot write in a read-only transaction");
    }
    triggers.accept(event.getPayload());
  }
}
