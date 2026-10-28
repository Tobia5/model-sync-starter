package io.modelsync.sample;

import io.modelsync.core.domain.Projection;
import io.modelsync.core.domain.SubjectKey;
import io.modelsync.core.port.ProjectionStore;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Test-only fault seam around the real, flushing JpaProjectionStore. */
final class AtomicityProjection implements ProjectionStore<Customer> {
  private final ProjectionStore<Customer> delegate;
  private final AtomicityFaults faults;
  private final JdbcTemplate jdbc;

  AtomicityProjection(ProjectionStore<Customer> delegate, AtomicityFaults faults, JdbcTemplate jdbc) {
    this.delegate = delegate;
    this.faults = faults;
    this.jdbc = jdbc;
  }

  @Override public Optional<Projection<Customer>> load(SubjectKey key) {
    return delegate.load(key);
  }

  @Override public boolean compareAndSet(SubjectKey key, String revision, Customer value, Instant expires) {
    boolean changed = delegate.compareAndSet(key, revision, value, expires);
    faults.projectionFlushed = changed;
    if (changed && faults.fault == AtomicityFault.AFTER_FLUSH) {
      throw new IllegalStateException("injected after real JPA flush");
    }
    if (changed && (faults.fault == AtomicityFault.BEFORE_COMMIT
        || faults.fault == AtomicityFault.ORACLE_COMMIT)) {
      TransactionSynchronizationManager.registerSynchronization(
          new AtomicityCompletion(faults, jdbc, CustomerJpaMapping.storageId(key)));
    }
    return changed;
  }
}
