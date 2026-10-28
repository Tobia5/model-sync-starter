package io.modelsync.sample;

import javax.sql.DataSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Configuration(proxyBeanMethods = false)
@Profile("atomicity-h2")
@Import(AtomicityConfiguration.class)
class H2AtomicityConfiguration {
  @Bean DataSource atomicityDataSource() {
    var source = new DriverManagerDataSource("jdbc:h2:mem:atomicity;DB_CLOSE_DELAY=-1", "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource("model-sync/schema/h2-v2.sql")).execute(source);
    new org.springframework.jdbc.core.JdbcTemplate(source).execute(
        "alter table MS_STATE add constraint MS_TEST_ATOMIC_STATE_CK check (owner_validator <> 'ledger-failure')");
    return source;
  }
}
