package io.modelsync.core;

import java.time.Clock;
import java.util.Optional;
/** Injectable per-model facade. Requesting sync returns a durable ticket, not a claim of completion. */
public final class ModelSync<T> {
    private final ModelBinding<T> binding; private final WorkStore store; private final LocalTransactions tx; private final Clock clock;
    ModelSync(ModelBinding<T> b,WorkStore s,LocalTransactions tx,Clock clock){binding=b;store=s;this.tx=tx;this.clock=clock;}
    public String request(String tenant,String id,Priority priority){return request(tenant,id,priority,false);}
    public String request(String tenant,String id,Priority priority,boolean force){
        WorkKey key=new WorkKey(binding.id(),new SubjectKey(tenant,id));
        return tx.required(()->store.enqueue(key,priority,force,clock.instant()));
    }
    public SyncState status(String tenant,String id){return tx.required(()->store.state(new WorkKey(binding.id(),new SubjectKey(tenant,id))));}
    public record LocalView<T>(Projection<T> projection, SyncState state, FreshnessPolicy.Age age) { }
    /** Diagnostic local snapshot. It may be expired, unverified or different from the owner. */
    public Optional<LocalView<T>> inspect(String tenant,String id){
        SubjectKey key=new SubjectKey(tenant,id);
        return tx.required(()->{
            SyncState state=store.state(new WorkKey(binding.id(),key));
            return binding.projection().load(key).map(p->{
                boolean matches=state.matched() && java.util.Objects.equals(binding.digest(p.value()),state.matchedDigest());
                FreshnessPolicy.Age age=matches?binding.freshness().age(state.validatedAt(),clock.instant()):FreshnessPolicy.Age.UNKNOWN;
                if(p.expired(clock.instant()))age=FreshnessPolicy.Age.EXPIRED;
                return new LocalView<>(p,state,age);
            });
        });
    }
    /** Local-only read: allows bounded staleness, never unknown data or age >= maxAge. */
    public Optional<Projection<T>> local(String tenant,String id){
        return inspect(tenant,id).filter(v->v.age()==FreshnessPolicy.Age.FRESH || v.age()==FreshnessPolicy.Age.STALE).map(LocalView::projection);
    }
    /** Local-only strict freshness; an empty result is not an instruction to do hidden I/O. */
    public Optional<Projection<T>> freshLocal(String tenant,String id){
        return inspect(tenant,id).filter(v->v.age()==FreshnessPolicy.Age.FRESH).map(LocalView::projection);
    }
    public ModelBinding<T> binding(){return binding;}
}
