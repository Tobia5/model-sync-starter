package io.modelsync.sample;

import io.modelsync.core.domain.Projection;
import io.modelsync.core.domain.SubjectKey;
import io.modelsync.core.port.ProjectionStore;
import java.time.Instant;
import java.util.Optional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class RecoveryProjection implements ProjectionStore<Customer> {
  private final ProjectionStore<Customer> delegate;
  private final RecoveryControl control;
  RecoveryProjection(ProjectionStore<Customer> delegate, RecoveryControl control) { this.delegate = delegate; this.control = control; }
  @Override public Optional<Projection<Customer>> load(SubjectKey key) { return delegate.load(key); }
  @Override public boolean compareAndSet(SubjectKey key, String revision, Customer value, Instant expires) {
    boolean changed = delegate.compareAndSet(key, revision, value, expires);
    if (changed) {
      control.checkpoint(RecoveryPoint.PROJECTION_FLUSHED);
      TransactionSynchronizationManager.registerSynchronization(new RecoverySynchronization(control));
    }
    return changed;
  }
}
