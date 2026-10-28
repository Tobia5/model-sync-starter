package io.modelsync.reactor;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** Reactor facade around a typed {@code ModelSync} instance. */
public final class ReactiveModelSync<T> {
  private final ModelSync<T> delegate;
  private final Scheduler persistence;

  ReactiveModelSync(ModelSync<T> delegate, Scheduler persistence) {
    this.delegate = delegate;
    this.persistence = persistence;
  }

  public Mono<String> request(String tenant, String id, Priority priority, boolean force) {
    return Mono.fromCallable(() -> delegate.request(tenant, id, priority, force))
        .subscribeOn(persistence);
  }

  public Mono<SyncState> status(String tenant, String id) {
    return Mono.fromCallable(() -> delegate.status(tenant, id)).subscribeOn(persistence);
  }

  public Mono<Projection<T>> local(String tenant, String id) {
    return Mono.fromCallable(() -> delegate.local(tenant, id))
        .subscribeOn(persistence)
        .flatMap(Mono::justOrEmpty);
  }
}
