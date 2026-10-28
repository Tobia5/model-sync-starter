package io.modelsync.jdbc;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** Synthetic lost acknowledgment AFTER a real commit, never an independent transport proof. */
final class CooldownCommitFaultManager extends DataSourceTransactionManager {
  boolean once=true;
  RuntimeException failure=new UncategorizedSQLException("Synthetic acknowledgment loss","redacted",new SQLException("redacted","61000",54));
  CooldownCommitFaultManager(DataSource source){super(source);}
  @Override protected void doCommit(DefaultTransactionStatus status) {
    super.doCommit(status);
    if(once && "modelSyncCooldownAccept".equals(status.getTransactionName())) {
      once=false;throw failure;
    }
  }
}
