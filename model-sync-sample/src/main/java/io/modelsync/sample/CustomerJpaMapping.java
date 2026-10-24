package io.modelsync.sample;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import io.modelsync.jpa.LockingJpaProjectionMapping;
import io.modelsync.jpa.JpaLockPolicy;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

/** Sample mapping between the synchronized customer DTO and the local JPA entity. */
public final class CustomerJpaMapping implements LockingJpaProjectionMapping<Customer, CustomerEntity> {
  static String storageId(SubjectKey key) {
    return new WorkKey("customer", key).storageId();
  }

  @Override
  public Optional<CustomerEntity> find(EntityManager em, SubjectKey key, LockModeType lock, JpaLockPolicy policy) {
    CustomerEntity e = policy == JpaLockPolicy.DEFAULT
        ? em.find(CustomerEntity.class, storageId(key), lock)
        : em.find(CustomerEntity.class, storageId(key), lock, policy.properties());
    if (e != null && (!e.tenant.equals(key.tenant()) || !e.sourceId.equals(key.id())))
      throw new IllegalStateException("Tenant/subject mismatch");
    return Optional.ofNullable(e);
  }

  @Override
  public Projection<Customer> snapshot(CustomerEntity e) {
    return new Projection<>(
        new Customer(e.displayName, e.status), Long.toString(e.version), e.expiresAt);
  }

  @Override
  public CustomerEntity create(SubjectKey key, Customer value, Instant expires) {
    CustomerEntity e = new CustomerEntity();
    e.storageKey = storageId(key);
    e.tenant = key.tenant();
    e.sourceId = key.id();
    update(e, value, expires);
    return e;
  }

  @Override
  public void update(CustomerEntity e, Customer value, Instant expires) {
    e.displayName = value.displayName();
    e.status = value.status();
    e.expiresAt = expires;
    // Never overwrite reviewRequestedAt: that field belongs to this application.
  }
}
