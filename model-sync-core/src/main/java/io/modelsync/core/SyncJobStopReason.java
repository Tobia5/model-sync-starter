package io.modelsync.core;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

public enum SyncJobStopReason { EMPTY, ITEM_LIMIT, TIME_BUDGET, INTERRUPTED, ALREADY_RUNNING }
