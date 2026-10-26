package example.consumer;

import io.modelsync.core.domain.Projection;
import io.modelsync.core.domain.SubjectKey;
import io.modelsync.core.domain.WorkKey;
import io.modelsync.jpa.JpaLockPolicy;
import io.modelsync.jpa.LockingJpaProjectionMapping;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;

/** Single-table ledger-first consumer mapping; explicitly consumes operation lock options. */
public final class ItemMapping implements LockingJpaProjectionMapping<ItemValue, ItemEntity> {
  private final ConsumerObservation observation;
  public ItemMapping(ConsumerObservation observation) { this.observation = observation; }
  @Override public Optional<ItemEntity> find(EntityManager em, SubjectKey key,
      LockModeType mode, JpaLockPolicy policy) {
    observation.mappingReads.incrementAndGet();
    observation.persistenceThreads.add(Thread.currentThread().getName());
    if (Thread.currentThread().getName().startsWith("reactor-http"))
      throw new IllegalStateException("Persistence on the event loop");
    if (mode == LockModeType.PESSIMISTIC_WRITE) observation.lookupPolicies.add(policy.name());
    var entity = em.find(ItemEntity.class, new WorkKey("item", key).storageId(), mode, policy.properties());
    if (entity != null && (!key.tenant().equals(entity.tenant) || !key.id().equals(entity.subject)))
      throw new IllegalStateException("Tenant/subject mismatch");
    return Optional.ofNullable(entity);
  }
  @Override public io.modelsync.core.domain.Projection<ItemValue> snapshot(ItemEntity entity) {
    return new Projection<>(new ItemValue(entity.value), Long.toString(entity.revision), entity.expires);
  }
  @Override public ItemEntity create(SubjectKey key, ItemValue value, Instant expires) {
    var entity = new ItemEntity(); entity.key = new WorkKey("item", key).storageId();
    entity.tenant = key.tenant(); entity.subject = key.id(); update(entity, value, expires); return entity;
  }
  @Override public void update(ItemEntity entity, ItemValue value, Instant expires) {
    entity.value = value.value(); entity.expires = expires;
  }
}
