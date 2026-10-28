package io.modelsync.testkit;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import java.time.*;
import java.util.*;

public record MemoryWorkCell(
    WorkKey key,
    Priority priority,
    long generation,
    int attempts,
    boolean force,
    String status,
    String token,
    Instant until,
    Instant due,
    String lastCode) {}
