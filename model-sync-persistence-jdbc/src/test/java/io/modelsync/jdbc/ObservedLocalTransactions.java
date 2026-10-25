package io.modelsync.jdbc;

import io.modelsync.core.port.LocalTransactions;
import java.util.function.Supplier;

/** Test-only observation outside the real transaction return/commit boundary. */
final class ObservedLocalTransactions implements LocalTransactions {
  private final LocalTransactions delegate;
  private final ComparisonAccounting accounting;
  private final ThreadLocal<ComparisonTransaction> frame = new ThreadLocal<>();
  ObservedLocalTransactions(LocalTransactions delegate, ComparisonAccounting accounting) {
    this.delegate = delegate; this.accounting = accounting;
  }
  ComparisonTransaction transaction() {
    var value = frame.get();
    if (value == null) throw new IllegalStateException("Observed store outside observed transaction");
    return value;
  }
  @Override public <T> T required(Supplier<T> work) {
    if (frame.get() != null) throw new IllegalStateException("Unexpected nested fixture transaction");
    var transaction = new ComparisonTransaction(); frame.set(transaction);
    var before = accounting.current();
    transaction.failureHandling = before.denials > 0 || !before.faults.isEmpty();
    T result;
    try { result = delegate.required(work); }
    catch (RuntimeException | Error failure) {
      var attempt = accounting.current();
      String site = transaction.claimStarted ? "CLAIM" : transaction.failureHandling
          ? "FAILURE_METADATA" : attempt.ownerCalls > 0 ? "RESULT" : "PREPARE";
      attempt.fault(site, failure);
      throw failure;
    } finally { accounting.current().fenceRejections += transaction.fenceRejections; frame.remove(); }
    // A successful callback alone is not a committed claim or completed reconciliation.
    var attempt = accounting.current();
    if (transaction.claimReturned) attempt.claims++;
    attempt.completions += transaction.completions;
    attempt.reschedules += transaction.reschedules;
    attempt.localConflicts += transaction.localConflicts;
    return result;
  }
  @Override public boolean active() { return delegate.active(); }
}
