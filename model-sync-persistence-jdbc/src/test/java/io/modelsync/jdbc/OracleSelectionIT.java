package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OracleSelectionIT extends QueueClaimChecks {
  private HikariDataSource pool;

  @BeforeAll void openPool() {
    assertEquals("DEDICATED_TEST_SCHEMA", System.getenv("MODELSYNC_ORACLE_TEST_SCHEMA_ACK"));
    String user = System.getenv("MODELSYNC_ORACLE_USER");
    assertNotNull(user);
    assertTrue(user.matches("MS_TEST_[A-Za-z0-9_]{1,20}"));
    var config = new HikariConfig();
    config.setJdbcUrl(System.getenv("MODELSYNC_ORACLE_URL"));
    config.setUsername(user);
    config.setPassword(System.getenv("MODELSYNC_ORACLE_PASSWORD"));
    config.setDriverClassName("oracle.jdbc.OracleDriver");
    config.setMaximumPoolSize(8);
    config.setMinimumIdle(8);
    config.setMaxLifetime(0);
    config.setPoolName("modelsync-selection");
    pool = new HikariDataSource(config);
  }

  @AfterAll void closePool() { if (pool != null) pool.close(); }

  @Override protected DataSource source() { return pool; }
}
