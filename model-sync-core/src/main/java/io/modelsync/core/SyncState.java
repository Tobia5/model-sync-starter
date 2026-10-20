package io.modelsync.core;

import java.time.Instant;
public record SyncState(WorkKey key, Instant validatedAt, Instant reconciledAt,
                        String matchedDigest, String validator, String outcome, Instant sourceChangedAt) {
    public static SyncState empty(WorkKey key){return new SyncState(key,null,null,null,null,"UNKNOWN",null);}
    public boolean matched(){return outcome.equals("MATCH") || outcome.equals("APPLIED") || outcome.equals("NOT_MODIFIED");}
}
