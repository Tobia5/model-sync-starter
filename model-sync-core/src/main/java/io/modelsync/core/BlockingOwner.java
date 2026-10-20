package io.modelsync.core;

@FunctionalInterface
public non-sealed interface BlockingOwner<T> extends OwnerPort<T> {
    /** One invocation = one quota unit. No hidden retries or redirects in standard adapters. */
    FetchResult<T> fetch(ReadRequest request) throws Exception;
}
