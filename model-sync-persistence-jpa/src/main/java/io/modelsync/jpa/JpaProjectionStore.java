package io.modelsync.jpa;

import io.modelsync.core.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Generic JPA CAS adapter. Use a shared EntityManager bound to the configured
 * JpaTransactionManager.
 */
public final class JpaProjectionStore<T, E> implements ProjectionStore<T> {
  private final EntityManager em;
  private final JpaProjectionMapping<T, E> mapping;

  public JpaProjectionStore(EntityManager em, JpaProjectionMapping<T, E> mapping) {
    this.em = em;
    this.mapping = mapping;
  }

  private void requireTransaction() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !em.isJoinedToTransaction())
      throw new IllegalStateException("JPA projection access requires a local transaction");
  }

  @Override
  public Optional<Projection<T>> load(SubjectKey key) {
    requireTransaction();
    return mapping.find(em, key, LockModeType.NONE).map(mapping::snapshot);
  }

  @Override
  public boolean compareAndSet(
      SubjectKey key, String expectedRevision, T value, Instant expiresAt) {
    requireTransaction();
    if (TransactionSynchronizationManager.isCurrentTransactionReadOnly())
      throw new IllegalStateException("Projection write in read-only transaction");
    Optional<E> found = mapping.find(em, key, LockModeType.PESSIMISTIC_WRITE);
    if (found.isEmpty()) {
      if (expectedRevision != null) return false;
      // Unique (tenant,subject) constraint resolves racing inserts; transaction rolls back on
      // conflict.
      em.persist(mapping.create(key, value, expiresAt));
    } else {
      E entity = found.get();
      // A previous load may have placed an older instance in the persistence context.
      em.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
      if (!Objects.equals(mapping.snapshot(entity).revision(), expectedRevision)) return false;
      mapping.update(entity, value, expiresAt);
    }
    em.flush();
    return true;
  }
}
