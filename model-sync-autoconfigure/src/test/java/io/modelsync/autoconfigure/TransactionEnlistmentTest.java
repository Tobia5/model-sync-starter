package io.modelsync.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class TransactionEnlistmentTest {
  @Test void jdbcLedgerRejectsJpaManagerWithDifferentDataSourceBeforeSchemaAccess() {
    DataSource ledger = new DriverManagerDataSource("jdbc:h2:mem:ledger", "sa", "");
    var manager = new UninitializedJpaTransactionManager();
    manager.setDataSource(new DriverManagerDataSource("jdbc:h2:mem:other", "sa", ""));
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ModelSyncJdbcAutoConfiguration.class))
        .withBean(DataSource.class, () -> ledger)
        // Avoid manager initialization: the validation must fail before any database operation.
        .withBean("transactionManager", org.springframework.transaction.PlatformTransactionManager.class,
            () -> manager)
        .withPropertyValues("model-sync.enabled=true")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure()).hasStackTraceContaining(
              "Model Sync ledger must use the transaction manager's DataSource");
        });
  }
}
