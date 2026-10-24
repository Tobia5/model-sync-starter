package io.modelsync.jdbc;

import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Full-only real Oracle candidates; legacy comparison stores remain separately identified. */
final class OracleQualificationStores {
  private OracleQualificationStores() {}

  static JdbcWorkStore work(JdbcTemplate jdbc) {
    return new JdbcWorkStore(jdbc, JdbcPointLockPolicy.ORACLE_NOWAIT, JdbcInsertPolicy.ORACLE_26_NOWAIT);
  }

  static JdbcQuotaGate quota(JdbcTemplate jdbc, PlatformTransactionManager manager, Clock clock) {
    return new JdbcQuotaGate(jdbc, manager, clock, JdbcQuotaLockPolicy.ORACLE_NOWAIT, JdbcInsertPolicy.ORACLE_26_NOWAIT);
  }
}
