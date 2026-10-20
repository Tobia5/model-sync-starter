package io.modelsync.core;

import java.time.Instant;
import java.util.Objects;

public enum FetchStatus {
  /** Owner returned the current value. */
  FOUND,
  /** Owner reported that the caller's validator is still current. */
  NOT_MODIFIED,
  /** Owner reported that the subject is absent. */
  ABSENT
}
