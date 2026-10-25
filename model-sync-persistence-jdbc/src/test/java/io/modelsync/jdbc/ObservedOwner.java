package io.modelsync.jdbc;

import io.modelsync.core.*;
import io.modelsync.core.port.BlockingOwner;

/** Guards one physical synthetic call with its own already-returned committed admission. */
final class ObservedOwner<T> implements BlockingOwner<T> {
  private final BlockingOwner<T> delegate;
  private final ComparisonAccounting accounting;
  ObservedOwner(BlockingOwner<T> delegate, ComparisonAccounting accounting) {
    this.delegate = delegate; this.accounting = accounting;
  }
  @Override public FetchResult<T> fetch(ReadRequest request) throws Exception {
    var attempt = accounting.current();
    if (attempt.claims != 1 || attempt.grants != 1 || attempt.acquisitionActive != 0
        || attempt.admissionUses != 0) {
      attempt.violations++;
      throw new AssertionError("Synthetic owner lacks a unique committed admission");
    }
    attempt.ownerCalls++; attempt.ownerActive++; attempt.admissionUses++;
    try {
      return delegate.fetch(request);
    } catch (Exception | Error failure) { attempt.fault("OWNER", failure); throw failure; }
    finally { attempt.ownerActive--; }
  }
}
