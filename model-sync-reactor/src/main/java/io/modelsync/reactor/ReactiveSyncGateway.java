package io.modelsync.reactor;

import io.modelsync.core.*;
import reactor.core.scheduler.Scheduler;
public final class ReactiveSyncGateway {
    private final SyncGateway gateway;private final Scheduler persistence;
    public ReactiveSyncGateway(SyncGateway gateway,Scheduler persistence){this.gateway=gateway;this.persistence=persistence;}
    public <T> ReactiveModelSync<T> model(Class<T> type){return new ReactiveModelSync<>(gateway.model(type),persistence);}
    public <T> ReactiveModelSync<T> model(String id,Class<T> type){return new ReactiveModelSync<>(gateway.model(id,type),persistence);}
}
