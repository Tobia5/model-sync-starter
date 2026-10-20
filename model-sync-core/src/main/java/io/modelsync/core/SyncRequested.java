package io.modelsync.core;
import io.modelsync.core.domain.*;

import java.util.Objects;

/** Refresh hint; deliberately carries identity, not an untrusted replacement payload. */
public record SyncRequested(String binding, SubjectKey subject, Priority priority, boolean force) {
  public SyncRequested {
    binding = SubjectKey.checked(binding, "binding", 128);
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(priority, "priority");
  }

  /** A source-change hint must not be skipped merely because the last fetch is recent. */
  public static SyncRequested changed(String binding, String tenant, String id) {
    return new SyncRequested(binding, new SubjectKey(tenant, id), Priority.HIGH, true);
  }
}
