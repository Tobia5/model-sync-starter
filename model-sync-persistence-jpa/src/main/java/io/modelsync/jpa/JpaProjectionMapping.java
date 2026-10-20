package io.modelsync.jpa;

import io.modelsync.core.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Optional;

/** Small application-owned mapping. All find operations MUST include tenant scoping. */
public interface JpaProjectionMapping<T, E> {
  Optional<E> find(EntityManager em, SubjectKey key, LockModeType lockMode);

  Projection<T> snapshot(E entity);

  E create(SubjectKey key, T value, Instant expiresAt);

  void update(E entity, T value, Instant expiresAt);
}
