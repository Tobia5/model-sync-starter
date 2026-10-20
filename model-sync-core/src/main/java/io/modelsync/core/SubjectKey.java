package io.modelsync.core;

import java.util.Objects;

/**
 * Opaque stable subject reference.
 *
 * <p>Subject keys identify tenant-scoped source records. They must not contain access tokens,
 * credentials, or other transient authorization material.
 *
 * @param tenant tenant identifier
 * @param id subject identifier within the tenant
 */
public record SubjectKey(String tenant, String id) {
  public SubjectKey {
    tenant = checked(tenant, "tenant", 128);
    id = checked(id, "id", 256);
  }

  static String checked(String value, String name, int max) {
    Objects.requireNonNull(value, name);
    if (value.isBlank() || value.length() > max || value.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Invalid " + name);
    return value;
  }
}
