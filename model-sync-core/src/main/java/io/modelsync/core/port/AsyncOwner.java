package io.modelsync.core.port;

import io.modelsync.core.*;
import io.modelsync.core.domain.*;

import java.util.concurrent.CompletionStage;

/**
 * Asynchronous authoritative-owner adapter.
 *
 * <p>Implementations should create work lazily when {@link #fetchAsync(ReadRequest)} is called and
 * should avoid hidden retries so provider quota accounting remains explicit.
 *
 * @param <T> synchronized model type returned by the owner
 */
@FunctionalInterface
public non-sealed interface AsyncOwner<T> extends OwnerPort<T> {
  /**
   * Fetches the authoritative value for a subject.
   *
   * @param request subject and optional validator from the last accepted owner response
   * @return completion stage containing the owner result
   */
  CompletionStage<FetchResult<T>> fetchAsync(ReadRequest request);
}
