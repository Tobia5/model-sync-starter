package io.modelsync.spring;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.port.LocalTransactions;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Spring transaction-manager implementation of the Model Sync local transaction boundary. */
public final class SpringLocalTransactions implements LocalTransactions {
  private final TransactionTemplate template;

  public SpringLocalTransactions(PlatformTransactionManager manager) {
    template = new TransactionTemplate(manager);
  }

  @Override
  public <T> T required(Supplier<T> work) {
    return template.execute(status -> work.get());
  }

  @Override
  public boolean active() {
    return TransactionSynchronizationManager.isActualTransactionActive();
  }
}
