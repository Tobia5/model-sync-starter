package io.modelsync.core;

import java.time.Instant;
public final class SyncExceptions {
    private SyncExceptions(){}
    public static final class LeaseLost extends RuntimeException { public LeaseLost(){super("Lease is no longer current");} }
    public static class RetryLater extends RuntimeException {
        private final Instant retryAt;
        public RetryLater(Instant retryAt){super("Deferred by admission policy");this.retryAt=java.util.Objects.requireNonNull(retryAt);}
        public Instant retryAt(){return retryAt;}
    }
    public static final class RateLimitedAfter extends RuntimeException {
        private final java.time.Duration delay;
        public RateLimitedAfter(java.time.Duration delay){
            super("Provider cooldown");this.delay=java.util.Objects.requireNonNull(delay);
            if(delay.isNegative())throw new IllegalArgumentException("Negative cooldown");
        }
        public java.time.Duration delay(){return delay;}
    }
    public static final class RateLimited extends RetryLater { public RateLimited(Instant t){super(t);} }
    public static final class Permanent extends RuntimeException {
        private final String code;
        public Permanent(String code){super(code);this.code=SubjectKey.checked(code,"errorCode",64);}
        public String code(){return code;}
    }
}
