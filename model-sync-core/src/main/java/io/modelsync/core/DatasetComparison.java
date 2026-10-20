package io.modelsync.core;

import java.math.BigDecimal;
import java.util.Map;

public enum DatasetComparison {
  /** Manifests describe the same resolved dataset content. */
  MATCH,
  /** Manifests are comparable and differ. */
  DIFFERENT,
  /** Manifests cannot establish equality because data or digest evidence is incomplete. */
  INCOMPLETE,
  /** Manifests describe different dataset identities. */
  INCOMPARABLE
}
