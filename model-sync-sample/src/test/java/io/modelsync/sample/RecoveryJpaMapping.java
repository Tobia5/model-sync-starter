package io.modelsync.sample;

import io.modelsync.core.domain.Projection;
import io.modelsync.core.domain.SubjectKey;
import io.modelsync.jpa.JpaProjectionMapping;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Fixture-owned mapping, explicit Oracle WAIT 1 instead of an unbounded JPA lock. */
final class RecoveryJpaMapping implements JpaProjectionMapping<Customer, CustomerEntity> {
  private final CustomerJpaMapping delegate = new CustomerJpaMapping();
  @Override public Optional<CustomerEntity> find(EntityManager em, SubjectKey key, LockModeType lock) {
    if (lock == LockModeType.NONE) return delegate.find(em, key, lock);
    CustomerEntity entity = em.find(CustomerEntity.class, CustomerJpaMapping.storageId(key), lock,
        Map.of("jakarta.persistence.lock.timeout", 1000, "jakarta.persistence.query.timeout", 2000));
    if (entity != null && (!entity.tenant.equals(key.tenant()) || !entity.sourceId.equals(key.id())))
      throw new IllegalStateException("Fixture tenant mismatch");
    return Optional.ofNullable(entity);
  }
  @Override public Projection<Customer> snapshot(CustomerEntity entity) { return delegate.snapshot(entity); }
  @Override public CustomerEntity create(SubjectKey key, Customer value, Instant expires) { return delegate.create(key, value, expires); }
  @Override public void update(CustomerEntity entity, Customer value, Instant expires) { delegate.update(entity, value, expires); }
}
