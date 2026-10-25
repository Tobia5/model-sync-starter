package io.modelsync.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/** Independent physical observations, after worker futures and synchronous owner calls exit. */
final class OraclePhysicalDrain {
  private OraclePhysicalDrain() {}
  static Map<String, Object> observe(JdbcTemplate jdbc, boolean threadsTerminated, int physicalOwners) {
    var source = (HikariDataSource)jdbc.getDataSource();
    return Map.of("worker_threads_terminated", threadsTerminated, "physical_owner_active", physicalOwners,
        "pool_active_connections", source.getHikariPoolMXBean().getActiveConnections(),
        "server_other_active_requests", jdbc.queryForObject("select count(*) from v$session where username=user"
            + " and sid<>to_number(sys_context('USERENV','SID')) and status='ACTIVE'", Integer.class),
        "server_transactions", jdbc.queryForObject("select count(*) from v$session where username=user and taddr is not null", Integer.class));
  }
  static void check(Map<String, Object> value) {
    if (!Boolean.TRUE.equals(value.get("worker_threads_terminated"))
        || value.entrySet().stream().filter(e -> e.getValue() instanceof Number).anyMatch(e -> ((Number)e.getValue()).intValue()!=0))
      throw new IllegalStateException("Comparison physical operations did not drain");
  }
}
