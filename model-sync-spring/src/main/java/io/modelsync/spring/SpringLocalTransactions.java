package io.modelsync.spring;

import io.modelsync.core.LocalTransactions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.function.Supplier;
public final class SpringLocalTransactions implements LocalTransactions {
    private final TransactionTemplate template;
    public SpringLocalTransactions(PlatformTransactionManager manager){template=new TransactionTemplate(manager);}
    @Override public <T> T required(Supplier<T> work){return template.execute(status->work.get());}
    @Override public boolean active(){return TransactionSynchronizationManager.isActualTransactionActive();}
}
