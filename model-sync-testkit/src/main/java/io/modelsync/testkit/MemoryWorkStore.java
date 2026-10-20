package io.modelsync.testkit;

import io.modelsync.core.*;
import java.time.*;
import java.util.*;
public final class MemoryWorkStore implements WorkStore,SnapshotResource {
    public record Cell(WorkKey key,Priority priority,long generation,int attempts,boolean force,
                       String status,String token,Instant until,Instant due,String lastCode){}
    private Map<String,Cell> rows=new LinkedHashMap<>();private Map<String,SyncState> states=new HashMap<>();
    public boolean failNextComplete;
    @Override public synchronized String enqueue(WorkKey key,Priority p,boolean force,Instant now){
        Objects.requireNonNull(p);String id=key.storageId();Cell old=rows.get(id);
        if(old==null)rows.put(id,new Cell(key,p,1,0,force,"WAITING",null,null,now,null));
        else{
            Priority best=old.priority().ordinal()<p.ordinal()?old.priority():p;
            rows.put(id,new Cell(key,best,old.generation()+1,old.status().equals("DEAD")?0:old.attempts(),old.force()||force,
                old.status().equals("LEASED")?"LEASED":"WAITING",old.token(),old.until(),now.isBefore(old.due())?now:old.due(),old.lastCode()));
        }
        return id;
    }
    @Override public synchronized Optional<WorkLease> claim(Priority p,Instant now,Duration duration){
        Optional<Cell> selected=rows.values().stream().filter(c->c.priority()==p)
            .filter(c->(c.status().equals("WAITING")&&!c.due().isAfter(now)) || (c.status().equals("LEASED")&&!c.until().isAfter(now)))
            .min(Comparator.comparing(Cell::due).thenComparing(c->c.key().storageId()));
        if(selected.isEmpty())return Optional.empty();Cell c=selected.get();String token=UUID.randomUUID().toString();Instant until=now.plus(duration);
        rows.put(c.key().storageId(),new Cell(c.key(),c.priority(),c.generation(),c.attempts(),c.force(),"LEASED",token,until,c.due(),c.lastCode()));
        return Optional.of(new WorkLease(c.key(),token,c.generation(),c.attempts(),c.force(),p,until));
    }
    @Override public synchronized boolean lockValid(WorkLease lease,Instant now){
        Cell c=rows.get(lease.key().storageId());return c!=null && c.status().equals("LEASED") && Objects.equals(c.token(),lease.token()) && c.until().isAfter(now);
    }
    @Override public synchronized SyncState state(WorkKey key){return states.getOrDefault(key.storageId(),SyncState.empty(key));}
    private Cell current(WorkLease lease){
        Cell c=rows.get(lease.key().storageId());
        if(c==null || !c.status().equals("LEASED") || !Objects.equals(c.token(),lease.token()))throw new SyncExceptions.LeaseLost();return c;
    }
    @Override public synchronized void complete(WorkLease lease,SyncState state,Instant nextAt,Instant now){
        if(failNextComplete){failNextComplete=false;throw new IllegalStateException("INJECTED_COMPLETION_FAILURE");}
        Cell c=current(lease);boolean pending=c.generation()!=lease.generation();states.put(lease.key().storageId(),state);
        rows.put(lease.key().storageId(),new Cell(c.key(),pending?c.priority():Priority.BACKGROUND,c.generation(),0,pending&&c.force(),"WAITING",null,null,pending?now:nextAt,null));
    }
    @Override public synchronized void reschedule(WorkLease lease,Instant nextAt,String code,boolean failure,int maxAttempts,Instant now){
        Cell c=current(lease);int attempts=c.generation()!=lease.generation()?0:c.attempts()+(failure?1:0);
        rows.put(lease.key().storageId(),new Cell(c.key(),c.priority(),c.generation(),attempts,c.force(),attempts>=maxAttempts?"DEAD":"WAITING",null,null,nextAt,code));
    }
    public synchronized Cell inspect(WorkKey key){return rows.get(key.storageId());}
    public synchronized int size(){return rows.size();}
    private record Snapshot(Map<String,Cell> rows,Map<String,SyncState> states){}
    @Override public synchronized Object snapshot(){return new Snapshot(new LinkedHashMap<>(rows),new HashMap<>(states));}
    @Override public synchronized void restore(Object before){Snapshot s=(Snapshot)before;rows=new LinkedHashMap<>(s.rows());states=new HashMap<>(s.states());}
}
