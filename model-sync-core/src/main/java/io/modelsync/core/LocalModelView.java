package io.modelsync.core;
import io.modelsync.core.domain.*;

import java.time.Clock;
import java.util.Optional;

public record LocalModelView<T>(Projection<T> projection, SyncState state, FreshnessAge age) {}
