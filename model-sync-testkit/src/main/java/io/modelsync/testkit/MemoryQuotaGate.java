package io.modelsync.testkit;

import io.modelsync.core.QuotaGate;
import java.time.*;
import java.util.*;
public final class MemoryQuotaGate implements QuotaGate {
    private record Quota(Duration spacing,Instant next){}
    private final Clock clock;private final Map<String,Quota> quotas=new HashMap<>();private long grants;
    public MemoryQuotaGate(Clock clock){this.clock=clock;}
    public synchronized void register(String group,Duration spacing){
        if(spacing.isNegative()||spacing.isZero())throw new IllegalArgumentException("Positive spacing required");
        Quota old=quotas.get(group);
        if(old!=null && !old.spacing().equals(spacing))throw new IllegalArgumentException("Quota mismatch");
        if(old==null)quotas.put(group,new Quota(spacing,Instant.MIN));
    }
    @Override public synchronized Admission acquire(String group){
        Quota q=Objects.requireNonNull(quotas.get(group),"Unknown quota");Instant now=clock.instant();
        if(now.isBefore(q.next()))return Admission.waitUntil(q.next());
        quotas.put(group,new Quota(q.spacing(),now.plus(q.spacing())));grants++;return Admission.allow();
    }
    @Override public synchronized void cooldown(String group,Instant until){
        Quota q=Objects.requireNonNull(quotas.get(group));quotas.put(group,new Quota(q.spacing(),until.isAfter(q.next())?until:q.next()));
    }
    public synchronized long grants(){return grants;}
}
