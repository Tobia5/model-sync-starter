package io.modelsync.core;

import java.util.function.Supplier;

/**
 * Shared local atomicity boundary.
 *
 * <p>Production implementations should ensure the JDBC ledger and projection store participate in
 * the same local transaction manager.
 */
public interface LocalTransactions {
  /**
   * Runs work in a required local transaction.
   *
   * @param work transaction-scoped work
   * @return result returned by the work
   * @param <T> result type
   */
  <T> T required(Supplier<T> work);

  /**
   * Reports whether the current thread is already inside the local transaction boundary.
   *
   * @return {@code true} when a local transaction is active
   */
  boolean active();
}
