package io.modelsync.sample;

import javax.sql.DataSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Configuration(proxyBeanMethods = false)
@Profile("atomicity-oracle")
@Import(AtomicityConfiguration.class)
class OracleAtomicityConfiguration {
  @Bean DataSource atomicityDataSource() {
    String user = System.getenv("MODELSYNC_ORACLE_USER");
    if (!"DEDICATED_TEST_SCHEMA".equals(System.getenv("MODELSYNC_ORACLE_TEST_SCHEMA_ACK"))
        || user == null || !user.matches("MS_TEST_[A-Za-z0-9_]{1,20}"))
      throw new IllegalStateException("Dedicated disposable Oracle runtime schema required");
    var source = new DriverManagerDataSource(System.getenv("MODELSYNC_ORACLE_URL"), user,
        System.getenv("MODELSYNC_ORACLE_PASSWORD"));
    source.setDriverClassName("oracle.jdbc.OracleDriver");
    var jdbc = new JdbcTemplate(source);
    if (jdbc.queryForObject("select count(*) from user_tables where table_name in ('SAMPLE_CUSTOMER','MS_TEST_ATOMIC_COMMIT')",
        Integer.class) != 0) throw new IllegalStateException("Atomicity fixture refuses existing projection/fault tables");
    new ResourceDatabasePopulator(new ClassPathResource("sample-oracle.sql")).execute(source);
    jdbc.execute("alter table MS_STATE add constraint MS_TEST_ATOMIC_STATE_CK check (owner_validator <> 'ledger-failure')");
    jdbc.execute("create table MS_TEST_ATOMIC_COMMIT (duplicate_value number(10),"
        + " constraint MS_TEST_ATOMIC_UQ unique (duplicate_value) deferrable initially deferred)");
    return source;
  }
}
