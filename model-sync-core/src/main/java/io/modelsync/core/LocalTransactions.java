package io.modelsync.core;

import java.util.function.Supplier;
/** Shared local atomicity boundary; JDBC ledger and JPA projection use the same transaction manager. */
public interface LocalTransactions {
    <T> T required(Supplier<T> work);
    boolean active();
}
