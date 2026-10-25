package io.modelsync.jdbc;

import java.util.concurrent.*;
import org.springframework.transaction.*;

/** Pauses one compliant transaction after its real SQL and before its real commit. */
public final class CooldownCommitLatch implements TransactionExecutionListener {
  private final String name;
  public final CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);
  public volatile boolean rollback;
  public CooldownCommitLatch(String name){this.name=name;}
  @Override public void beforeCommit(TransactionExecution tx) {
    if(!name.equals(tx.getTransactionName()))return;
    held.countDown();
    try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Owned competitor bound");}
    catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Owned competitor interrupted",e);}
    if(rollback)throw new IllegalStateException("Synthetic competitor rollback");
  }
}
