package io.modelsync.core.application;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;

import java.util.Objects;

/** Shared admission API for events and visits. All paths reuse the existing durable work ledger. */
public final class SyncTriggers {
  private final SyncGateway gateway;

  public SyncTriggers(SyncGateway gateway) {
    this.gateway = Objects.requireNonNull(gateway, "gateway");
  }

  /** Returns a subject ticket after enqueue; the surrounding transaction may still roll back. */
  public String accept(SyncRequested request) {
    Objects.requireNonNull(request, "request");
    return gateway.request(request.binding(), request.subject().tenant(), request.subject().id(),
        request.priority(), request.force());
  }

  public <T> SyncAccess<T> model(String binding, Class<T> type) {
    return new SyncAccess<>(gateway.model(binding, type));
  }

  public <T> SyncAccess<T> model(Class<T> type) {
    return new SyncAccess<>(gateway.model(type));
  }
}
