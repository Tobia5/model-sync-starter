package io.modelsync.jpa;

import java.util.Map;

/** Per-operation pessimistic lock options. This does not configure flush/insert/transport bounds. */
public enum JpaLockPolicy {
  DEFAULT,
  ORACLE_NOWAIT;

  /** @return immutable JPA lock properties for this operation, never global EntityManager settings */
  public Map<String, Object> properties() {
    return this == ORACLE_NOWAIT ? Map.of("jakarta.persistence.lock.timeout", 0) : Map.of();
  }
}
