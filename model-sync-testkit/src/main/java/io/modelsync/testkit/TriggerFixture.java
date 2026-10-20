package io.modelsync.testkit;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public record TriggerFixture(CoreFixture base, ModelBinding<String> binding, SyncCoordinator coordinator,
                       SyncTriggers triggers, ModelSync<String> model, SyncAccess<String> access) {
  void run() { if (!coordinator.runOne()) throw new AssertionError("Expected one work item"); }
  WorkKey key() { return base.key; }
  void seed() { triggers.accept(SyncRequested.changed("customer", "tenant-a", "42")); run(); }
  VisitResult<String> visit(VisitPolicy policy) { return access.visit("tenant-a", "42", policy); }
}
