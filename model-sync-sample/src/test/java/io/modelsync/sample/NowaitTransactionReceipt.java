package io.modelsync.sample;

import org.springframework.transaction.support.TransactionSynchronization;

final class NowaitTransactionReceipt implements TransactionSynchronization {
  volatile int status = -1;
  @Override public void afterCompletion(int completionStatus) { status = completionStatus; }
}
