package io.modelsync.sample;

import io.modelsync.core.domain.Projection;
import io.modelsync.core.domain.SubjectKey;
import io.modelsync.jpa.JpaProjectionMapping;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Lookup, refresh-isolation and extra-row flush fixture; never a shipped mapping. */
final class NowaitJpaMapping implements JpaProjectionMapping<Customer, CustomerEntity> {
  private final CustomerJpaMapping delegate = new CustomerJpaMapping();
  private final NowaitSqlProbe trace;
  private final EntityManager em;
  NowaitJpaMapping(EntityManager em, NowaitSqlProbe trace) { this.em = em; this.trace = trace; }

  @Override public Optional<CustomerEntity> find(EntityManager manager, SubjectKey key, LockModeType mode) {
    LockModeType actual = trace.deferLookup ? LockModeType.NONE : mode;
    CustomerEntity entity = manager.find(CustomerEntity.class, CustomerJpaMapping.storageId(key), actual,
        Map.of("jakarta.persistence.lock.timeout", 0));
    if (entity != null && (!entity.tenant.equals(key.tenant()) || !entity.sourceId.equals(key.id())))
      throw new IllegalStateException("Fixture identity mismatch");
    return Optional.ofNullable(entity);
  }
  @Override public Projection<Customer> snapshot(CustomerEntity entity) { return delegate.snapshot(entity); }
  @Override public CustomerEntity create(SubjectKey key, Customer value, Instant expires) { return delegate.create(key, value, expires); }
  @Override public void update(CustomerEntity entity, Customer value, Instant expires) {
    trace.updates++; delegate.update(entity, value, expires);
    if (trace.extra != null) {
      CustomerEntity extra = em.find(CustomerEntity.class, CustomerJpaMapping.storageId(trace.extra));
      extra.displayName = "attempt-extra";
    }
  }
}
