package io.modelsync.sample;

final class AtomicityFaults {
  AtomicityFault fault = AtomicityFault.NONE;
  boolean completionReached;
  boolean projectionFlushed;
  boolean deferredStatementsAccepted;
  int completionStatus = -1;

  void reset() {
    fault = AtomicityFault.NONE;
    completionReached = false;
    projectionFlushed = false;
    deferredStatementsAccepted = false;
    completionStatus = -1;
  }
}
