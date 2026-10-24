package io.modelsync.core;

/** Read acceptance policy, not a network timeout or a scheduling policy. */
public enum VisitPolicy {
  /** Serve verified data below maxAge while requesting a refresh after refreshAfter. */
  STALE_WHILE_REVALIDATE,
  /** Serve only data below refreshAfter; return a refresh ticket otherwise. */
  REQUIRE_FRESH
}
