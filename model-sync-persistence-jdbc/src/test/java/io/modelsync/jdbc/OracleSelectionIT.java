package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class OracleSelectionIT extends QueueClaimChecks {
  @Override protected DataSource source() {
    assertEquals("DEDICATED_TEST_SCHEMA", System.getenv("MODELSYNC_ORACLE_TEST_SCHEMA_ACK"));
    String user = System.getenv("MODELSYNC_ORACLE_USER");
    assertNotNull(user);
    assertTrue(user.matches("MS_TEST_[A-Za-z0-9_]{1,20}"));
    var source = new DriverManagerDataSource(System.getenv("MODELSYNC_ORACLE_URL"), user,
        System.getenv("MODELSYNC_ORACLE_PASSWORD"));
    source.setDriverClassName("oracle.jdbc.OracleDriver");
    return source;
  }
}
