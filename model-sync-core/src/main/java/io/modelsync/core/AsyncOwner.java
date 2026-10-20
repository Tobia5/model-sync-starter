package io.modelsync.core;

import java.util.concurrent.CompletionStage;
@FunctionalInterface
public non-sealed interface AsyncOwner<T> extends OwnerPort<T> {
    /** Must start lazily when called, and return a cancellable future where possible. */
    CompletionStage<FetchResult<T>> fetchAsync(ReadRequest request);
}
