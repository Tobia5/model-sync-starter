package io.modelsync.sample;

import org.springframework.transaction.support.TransactionSynchronization;

final class RecoverySynchronization implements TransactionSynchronization {
  private final RecoveryControl control;
  RecoverySynchronization(RecoveryControl control) { this.control = control; }
  @Override public void beforeCommit(boolean readOnly) {
    if (control.commitProxy != null) control.commitProxy.arm(control.holdCommitUp);
  }
  @Override public void afterCommit() { control.checkpoint(RecoveryPoint.COMPLETION_COMMITTED); }
}
