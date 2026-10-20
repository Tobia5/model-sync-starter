package io.modelsync.core;

import java.util.List;

public enum IdentityResolutionStatus {
  /** A single canonical identity was established. */
  RESOLVED,
  /** No canonical identity is available. */
  UNRESOLVED,
  /** Multiple possible identities exist and require review. */
  AMBIGUOUS
}
