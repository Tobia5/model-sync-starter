package io.modelsync.jpa;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

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
  private final JpaLockPolicy lockPolicy;

  public JpaProjectionStore(EntityManager em, JpaProjectionMapping<T, E> mapping) {
    this(em, mapping, JpaLockPolicy.DEFAULT);
  }

  /**
   * @param em shared manager enlisted with the JDBC ledger's local transaction
   * @param mapping tenant-scoped mapping; opt-in requires LockingJpaProjectionMapping
   * @param lockPolicy per-lookup/refresh policy, not a flush/insert or whole-transaction deadline
   */
  public JpaProjectionStore(EntityManager em, JpaProjectionMapping<T, E> mapping, JpaLockPolicy lockPolicy) {
    this.em = Objects.requireNonNull(em);
    this.mapping = Objects.requireNonNull(mapping);
    this.lockPolicy = Objects.requireNonNull(lockPolicy);
    if (lockPolicy != JpaLockPolicy.DEFAULT && !(mapping instanceof LockingJpaProjectionMapping<?, ?>))
      throw new IllegalArgumentException("Opted-in JPA locking requires LockingJpaProjectionMapping; no fallback");
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
    try { return apply(key, expectedRevision, value, expiresAt); }
    catch (RuntimeException failure) {
      if (lockPolicy == JpaLockPolicy.ORACLE_NOWAIT && oracleLockRefusal(failure))
        throw new LocalContention("projection lookup/refresh/flush", failure);
      throw failure;
    }
  }

  @SuppressWarnings("unchecked")
  private boolean apply(SubjectKey key, String expectedRevision, T value, Instant expiresAt) {
    Optional<E> found = lockPolicy == JpaLockPolicy.DEFAULT
        ? mapping.find(em, key, LockModeType.PESSIMISTIC_WRITE)
        : ((LockingJpaProjectionMapping<T, E>) mapping).find(em, key, LockModeType.PESSIMISTIC_WRITE, lockPolicy);
    if (found.isEmpty()) {
      if (expectedRevision != null) return false;
      // Unique (tenant,subject) constraint resolves racing inserts; transaction rolls back on
      // conflict.
      em.persist(mapping.create(key, value, expiresAt));
    } else {
      E entity = found.get();
      // A previous load may have placed an older instance in the persistence context.
      if (lockPolicy == JpaLockPolicy.DEFAULT) em.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
      else em.refresh(entity, LockModeType.PESSIMISTIC_WRITE, lockPolicy.properties());
      if (!Objects.equals(mapping.snapshot(entity).revision(), expectedRevision)) return false;
      mapping.update(entity, value, expiresAt);
    }
    em.flush();
    return true;
  }

  static boolean oracleLockRefusal(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause())
      if (cause instanceof java.sql.SQLException sql && sql.getErrorCode() == 54) return true;
    return false;
  }
}
