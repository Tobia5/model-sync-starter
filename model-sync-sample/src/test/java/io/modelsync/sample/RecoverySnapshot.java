package io.modelsync.sample;

record RecoverySnapshot(String name, long version, String validator, String outcome,
    String status, String token, long generation, long leaseUntil, boolean locallyMarked) {}
