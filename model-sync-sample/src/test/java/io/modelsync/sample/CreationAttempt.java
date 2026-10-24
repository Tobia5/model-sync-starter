package io.modelsync.sample;

import java.util.Map;

/** Detached result of one owned physical test operation. */
record CreationAttempt(Map<String, Object> evidence, String ticket, Throwable failure) {}
