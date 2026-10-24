package io.modelsync.sample;

import java.util.Map;

record LockPolicyAttempt(Map<String, Object> evidence, Throwable failure, int sid, int serial) {}
