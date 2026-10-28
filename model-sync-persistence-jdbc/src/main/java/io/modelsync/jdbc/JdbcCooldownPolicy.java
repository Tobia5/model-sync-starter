package io.modelsync.jdbc;

/** DIRECT preserves the existing API/schema. DURABLE requires the explicit cooldown-v1 migration. */
public enum JdbcCooldownPolicy { DIRECT, DURABLE }
