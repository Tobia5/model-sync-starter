package io.modelsync.jdbc;

/** Explicit point-lock SQL policy; queue SKIP LOCKED and INSERT/UPDATE SQL are unaffected. */
public enum JdbcPointLockPolicy {
  DEFAULT,
  ORACLE_NOWAIT;

  String sql() {
    return "select * from MS_WORK where work_id=? for update"
        + (this == ORACLE_NOWAIT ? " nowait" : "");
  }
}
