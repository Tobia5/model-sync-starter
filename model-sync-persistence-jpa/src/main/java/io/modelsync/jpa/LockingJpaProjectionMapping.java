package io.modelsync.jpa;

import io.modelsync.core.domain.SubjectKey;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;

/**
 * Explicit capability for per-operation locking. Implementations must honor the supplied policy
 * on every pessimistic lookup, preserve tenant scoping and qualify generated SQL on their provider.
 * Implementing this interface is an explicit contract, not proof that a provider honors its hints.
 */
public interface LockingJpaProjectionMapping<T, E> extends JpaProjectionMapping<T, E> {
  /**
   * @param em transaction-bound manager
   * @param key tenant-scoped identity
   * @param mode requested lock mode
   * @param policy explicit per-operation policy, including its JPA properties
   * @return matching entity, if present; missing-row inserts need separate containment
   */
  Optional<E> find(EntityManager em, SubjectKey key, LockModeType mode, JpaLockPolicy policy);

  @Override
  default Optional<E> find(EntityManager em, SubjectKey key, LockModeType mode) {
    return find(em, key, mode, JpaLockPolicy.DEFAULT);
  }
}
