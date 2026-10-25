package io.modelsync.sample;

import java.util.concurrent.*;
import org.springframework.transaction.*;

/** Independent compliant admission held after real quota DML and before commit. */
final class CooldownWorkerLatch implements TransactionExecutionListener {
  final CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);
  @Override public void beforeCommit(TransactionExecution tx) {
    if(!"modelSyncQuota".equals(tx.getTransactionName()))return;
    held.countDown();try {if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Owned quota competitor bound");}
    catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
  }
}
