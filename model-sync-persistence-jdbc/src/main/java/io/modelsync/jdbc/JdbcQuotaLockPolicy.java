package io.modelsync.jdbc;

/** Existing quota-row locking only; registration inserts and transport settings are unchanged. */
public enum JdbcQuotaLockPolicy {
  DEFAULT,
  ORACLE_NOWAIT;

  String suffix() { return this == ORACLE_NOWAIT ? " nowait" : ""; }
}
