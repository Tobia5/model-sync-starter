package io.modelsync.reactor;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.application.SyncAccess;
import io.modelsync.core.SyncRequested;
import io.modelsync.core.application.SyncTriggers;
import io.modelsync.core.VisitPolicy;
import io.modelsync.core.VisitResult;
import java.util.Objects;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** Lazy WebFlux bridge for local JPA/JDBC visit work; does not make JPA nonblocking. */
public final class ReactiveSyncAccess<T> {
  private final SyncAccess<T> delegate;
  private final Scheduler persistence;

  public ReactiveSyncAccess(SyncAccess<T> delegate, Scheduler persistence) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.persistence = Objects.requireNonNull(persistence, "persistence");
  }

  public Mono<VisitResult<T>> visit(String tenant, String id, VisitPolicy policy) {
    return Mono.fromCallable(() -> delegate.visit(tenant, id, policy)).subscribeOn(persistence);
  }

  /** Broker consumers must ACK only after this Mono completes successfully and its transaction commits. */
  public static Mono<String> accept(SyncTriggers triggers, SyncRequested event, Scheduler persistence) {
    Objects.requireNonNull(triggers, "triggers");
    Objects.requireNonNull(event, "event");
    Objects.requireNonNull(persistence, "persistence");
    return Mono.fromCallable(() -> triggers.accept(event)).subscribeOn(persistence);
  }
}
