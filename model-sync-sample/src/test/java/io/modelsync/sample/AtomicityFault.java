package io.modelsync.sample;

enum AtomicityFault {
  NONE, AFTER_FLUSH, LEDGER_WRITE, BEFORE_COMMIT, ORACLE_COMMIT
}
