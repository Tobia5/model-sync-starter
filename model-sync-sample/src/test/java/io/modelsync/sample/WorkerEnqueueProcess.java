package io.modelsync.sample;

import io.modelsync.core.Priority;
import io.modelsync.core.domain.SubjectKey;
import io.modelsync.core.domain.WorkKey;
import io.modelsync.jdbc.JdbcWorkStore;
import io.modelsync.jdbc.JdbcPointLockPolicy;
import io.modelsync.jdbc.JdbcInsertPolicy;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Independent process using the public ledger port. No credentials in arguments or output. */
public final class WorkerEnqueueProcess {
  private WorkerEnqueueProcess() {}
  public static void main(String[] args) {
    if (!"DEDICATED_TEST_SCHEMA".equals(System.getenv("MODELSYNC_ORACLE_TEST_SCHEMA_ACK")))
      throw new IllegalStateException("Disposable schema acknowledgment required");
    var source = new DriverManagerDataSource(System.getenv("MODELSYNC_ORACLE_URL"),
        System.getenv("MODELSYNC_ORACLE_USER"), System.getenv("MODELSYNC_ORACLE_PASSWORD"));
    var store = new JdbcWorkStore(new JdbcTemplate(source), JdbcPointLockPolicy.ORACLE_NOWAIT, JdbcInsertPolicy.ORACLE_26_NOWAIT);
    long before = System.currentTimeMillis();
    new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status ->
        store.enqueue(new WorkKey("worker-main", new SubjectKey("synthetic-worker", "external")),
            Priority.URGENT, true, Instant.now()));
    System.out.println("SYNTHETIC_ENQUEUE_COMMITTED " + before + " " + System.currentTimeMillis());
  }
}
