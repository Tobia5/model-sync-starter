package io.modelsync.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import io.modelsync.core.application.ModelRegistry;
import io.modelsync.core.port.*;
import io.modelsync.jdbc.*;
import io.modelsync.jpa.JpaLockPolicy;
import io.modelsync.testkit.CoreFixture;
import java.time.Clock;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;

class LockPolicyConfigurationTest {
  @Test void creationCapabilityIsExplicitVersionedAndReplaceable() {
    var source=new DriverManagerDataSource("jdbc:h2:mem:creation-config;DB_CLOSE_DELAY=-1","sa","");new ResourceDatabasePopulator(new ClassPathResource("model-sync/schema/h2-v2.sql")).execute(source);var f=new CoreFixture();
    var runner=new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ModelSyncJdbcAutoConfiguration.class))
        .withBean(DataSource.class,()->source).withBean(PlatformTransactionManager.class,()->new DataSourceTransactionManager(source))
        .withBean(ModelRegistry.class,()->f.registry).withBean("modelSyncClock",Clock.class,()->f.clock).withPropertyValues("model-sync.enabled=true","model-sync.quotas.owner=1ms");
    runner.run(c->{assertThat(c).hasNotFailed();assertThat(c.getBean(ModelSyncJdbcProperties.class).getInsertPolicy()).isEqualTo(JdbcInsertPolicy.DEFAULT);});
    runner.withPropertyValues("model-sync.jdbc.insert-policy=ORACLE_26_NOWAIT").run(c->assertThat(c.getStartupFailure()).hasStackTraceContaining("qualified Oracle26ai"));
    runner.withBean(WorkStore.class,()->f.store).withBean(QuotaGate.class,()->f.gate).withPropertyValues("model-sync.jdbc.insert-policy=ORACLE_26_NOWAIT")
        .run(c->{assertThat(c).hasNotFailed();assertThat(c.getBean(WorkStore.class)).isSameAs(f.store);assertThat(c.getBean(QuotaGate.class)).isSameAs(f.gate);});
  }
  @Test void quotaPolicyIsSeparateAndCustomGateBacksOff() {
    var source = new DriverManagerDataSource("jdbc:h2:mem:quota-config;DB_CLOSE_DELAY=-1", "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource("model-sync/schema/h2-v2.sql")).execute(source);
    var f = new CoreFixture();
    var runner = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ModelSyncJdbcAutoConfiguration.class))
        .withBean(DataSource.class, () -> source)
        .withBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(source))
        .withBean(WorkStore.class, () -> f.store).withBean(ModelRegistry.class, () -> f.registry)
        .withBean("modelSyncClock", Clock.class, () -> f.clock)
        .withPropertyValues("model-sync.enabled=true", "model-sync.quotas.owner=1ms");
    runner.run(c -> { assertThat(c).hasNotFailed(); assertThat(c.getBean(ModelSyncJdbcProperties.class).getQuotaLockPolicy()).isEqualTo(JdbcQuotaLockPolicy.DEFAULT); });
    runner.withPropertyValues("model-sync.jdbc.quota-lock-policy=ORACLE_NOWAIT")
        .run(c -> assertThat(c.getStartupFailure()).hasStackTraceContaining("quota locks require an Oracle datasource"));
    runner.withBean(QuotaGate.class, () -> f.gate).withPropertyValues("model-sync.jdbc.quota-lock-policy=ORACLE_NOWAIT")
        .run(c -> { assertThat(c).hasNotFailed(); assertThat(c.getBean(QuotaGate.class)).isSameAs(f.gate); });
  }
  private ApplicationContextRunner jpa() {
    return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ModelSyncJpaAutoConfiguration.class));
  }
  @Test void jpaPolicyDefaultsToCompatibleAndIsExplicitlyReplaceable() {
    jpa().withPropertyValues("model-sync.enabled=true").run(c -> assertThat(c.getBean(JpaLockPolicy.class)).isEqualTo(JpaLockPolicy.DEFAULT));
    jpa().withPropertyValues("model-sync.enabled=true", "model-sync.jpa.lock-policy=ORACLE_NOWAIT")
        .run(c -> assertThat(c.getBean(JpaLockPolicy.class)).isEqualTo(JpaLockPolicy.ORACLE_NOWAIT));
    jpa().withPropertyValues("model-sync.enabled=true", "model-sync.jpa.lock-policy=ORACLE_NOWAIT")
        .withBean(JpaLockPolicy.class, () -> JpaLockPolicy.DEFAULT)
        .run(c -> assertThat(c.getBean(JpaLockPolicy.class)).isEqualTo(JpaLockPolicy.DEFAULT));
  }
  @Test void disabledOrAbsentJpaDoesNotCreatePolicy() {
    jpa().run(c -> assertThat(c).doesNotHaveBean(JpaLockPolicy.class));
    jpa().withClassLoader(new FilteredClassLoader("io.modelsync.jpa"))
        .withPropertyValues("model-sync.enabled=true").run(c -> assertThat(c).hasNotFailed());
  }
  @Test void jdbcDefaultWorksButOracleOptInRejectsH2AndCustomStoreBacksOff() {
    var source = new DriverManagerDataSource("jdbc:h2:mem:policy-config;DB_CLOSE_DELAY=-1", "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource("model-sync/schema/h2-v2.sql")).execute(source);
    var f = new CoreFixture();
    var runner = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ModelSyncJdbcAutoConfiguration.class))
        .withBean(DataSource.class, () -> source)
        .withBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(source))
        .withBean(QuotaGate.class, () -> f.gate).withBean(ModelRegistry.class, () -> f.registry)
        .withBean("modelSyncClock", Clock.class, () -> f.clock).withPropertyValues("model-sync.enabled=true");
    runner.run(c -> assertThat(c.getBean(WorkStore.class)).isInstanceOf(JdbcWorkStore.class));
    runner.withPropertyValues("model-sync.jdbc.point-lock-policy=ORACLE_NOWAIT")
        .run(c -> assertThat(c.getStartupFailure()).hasStackTraceContaining("require an Oracle datasource"));
    runner.withBean(WorkStore.class, () -> f.store).withPropertyValues("model-sync.jdbc.point-lock-policy=ORACLE_NOWAIT")
        .run(c -> assertThat(c.getBean(WorkStore.class)).isSameAs(f.store));
  }
}
