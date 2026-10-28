package io.modelsync.jdbc;

import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class QueueClaimTest extends QueueClaimChecks {
  @Override protected DataSource source() {
    var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource("model-sync/schema/h2-v2.sql")).execute(source);
    return source;
  }
}
