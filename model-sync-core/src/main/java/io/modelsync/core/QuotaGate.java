package io.modelsync.core;

import java.time.Instant;
public interface QuotaGate {
    record Admission(boolean granted, Instant retryAt) {
        public static Admission allow(){return new Admission(true,null);}
        public static Admission waitUntil(Instant t){return new Admission(false,t);}
    }
    Admission acquire(String group);
    void cooldown(String group, Instant until);
}
