package io.modelsync.testkit;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import java.time.*;
import java.util.*;

public record MemoryWorkSnapshot(Map<String, MemoryWorkCell> rows, Map<String, SyncState> states) {}
