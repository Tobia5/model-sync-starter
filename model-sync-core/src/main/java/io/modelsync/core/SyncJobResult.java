package io.modelsync.core;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

public record SyncJobResult(int attempted, SyncJobStopReason stopReason) { }
