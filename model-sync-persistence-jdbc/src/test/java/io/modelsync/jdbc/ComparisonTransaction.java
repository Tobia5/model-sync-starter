package io.modelsync.jdbc;

/** Provisional adapter operations become observations only after required() commits. */
final class ComparisonTransaction {
  boolean claimStarted, claimReturned, rescheduleStarted;
  boolean failureHandling;
  int completions, reschedules, fenceRejections, localConflicts;
}
