package io.modelsync.sample;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;

/** Failure at Spring's completion boundary, or Oracle's actual deferred-constraint commit. */
final class AtomicityCompletion implements TransactionSynchronization {
  private final AtomicityFaults faults;
  private final JdbcTemplate jdbc;
  private final String workId;

  AtomicityCompletion(AtomicityFaults faults, JdbcTemplate jdbc, String workId) {
    this.faults = faults;
    this.jdbc = jdbc;
    this.workId = workId;
  }

  @Override public void beforeCommit(boolean readOnly) {
    // Both writes must have reached this transaction before testing its completion failure.
    if (jdbc.queryForObject("select count(*) from MS_STATE where work_id=? and owner_validator='after'",
        Integer.class, workId) != 1
        || jdbc.queryForObject("select count(*) from SAMPLE_CUSTOMER where storage_key=? and display_name='after'",
        Integer.class, workId) != 1) {
      throw new AssertionError("Completion injection did not observe both uncommitted writes");
    }
    faults.completionReached = true;
    if (faults.fault == AtomicityFault.ORACLE_COMMIT) {
      // Oracle accepts these statements and rejects the deferred UNIQUE constraint at COMMIT.
      jdbc.update("insert into MS_TEST_ATOMIC_COMMIT (duplicate_value) values (1)");
      jdbc.update("insert into MS_TEST_ATOMIC_COMMIT (duplicate_value) values (1)");
      faults.deferredStatementsAccepted = true;
    } else {
      throw new IllegalStateException("injected beforeCommit failure");
    }
  }

  @Override public void afterCompletion(int status) {
    faults.completionStatus = status;
  }
}
