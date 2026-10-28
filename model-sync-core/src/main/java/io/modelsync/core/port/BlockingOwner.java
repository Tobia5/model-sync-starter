package io.modelsync.core.port;

import io.modelsync.core.*;
import io.modelsync.core.domain.*;

/**
 * Blocking authoritative-owner adapter.
 *
 * <p>One invocation represents one provider-admission unit. Standard adapters should not retry or
 * redirect internally because the coordinator owns retry and cooldown behavior.
 *
 * @param <T> synchronized model type returned by the owner
 */
@FunctionalInterface
public non-sealed interface BlockingOwner<T> extends OwnerPort<T> {
  /**
   * Fetches the authoritative value for a subject.
   *
   * @param request subject and optional validator from the last accepted owner response
   * @return owner result describing found, unchanged, or absent data
   * @throws Exception when the owner call or adapter fails
   */
  FetchResult<T> fetch(ReadRequest request) throws Exception;
}
