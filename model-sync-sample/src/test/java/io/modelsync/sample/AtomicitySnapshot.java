package io.modelsync.sample;

record AtomicitySnapshot(String name, long version, String validator, String outcome,
                         String workStatus, String token, long generation, int attempts) {}
