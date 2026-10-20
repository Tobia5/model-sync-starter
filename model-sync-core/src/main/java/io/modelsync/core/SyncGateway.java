package io.modelsync.core;

import java.time.Clock;
import java.util.List;
public final class SyncGateway {
    private final ModelRegistry registry;private final WorkStore store;private final LocalTransactions tx;private final Clock clock;
    public SyncGateway(ModelRegistry registry,WorkStore store,LocalTransactions tx,Clock clock){this.registry=registry;this.store=store;this.tx=tx;this.clock=clock;}
    public ModelBinding<?> binding(String id){return registry.get(id);}
    public <T> ModelSync<T> model(Class<T> type){return new ModelSync<>(registry.get(type),store,tx,clock);}
    public <T> ModelSync<T> model(String binding,Class<T> type){return new ModelSync<>(registry.get(binding,type),store,tx,clock);}
    public String request(String binding,String tenant,String id,Priority p,boolean force){
        registry.get(binding);
        return tx.required(()->store.enqueue(new WorkKey(binding,new SubjectKey(tenant,id)),p,force,clock.instant()));
    }
    /** Registered model types, NOT every row at the external owner. */
    public List<ModelBinding<?>> boundaryModels(String boundary){return registry.boundary(boundary);}
}
