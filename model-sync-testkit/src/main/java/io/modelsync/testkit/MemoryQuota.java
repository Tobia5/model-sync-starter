package io.modelsync.testkit;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.port.QuotaGate;
import java.time.*;
import java.util.*;

public record MemoryQuota(Duration spacing, Instant next) {}
