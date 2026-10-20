package io.modelsync.core;

import java.util.List;

/**
 * Application-specific identity mapping seam.
 *
 * <p>Implementations should use authoritative matching rules and must not guess a canonical subject
 * from an ambiguous match.
 */
@FunctionalInterface
public interface IdentityResolver {
  /**
   * Resolves a source-specific subject into a canonical identity.
   *
   * @param sourceSystem external source system identifier
   * @param sourceKey subject key in the source system
   * @return resolution result with status and mapping evidence
   */
  Resolution resolve(String sourceSystem, SubjectKey sourceKey);

  /**
   * Identity resolution result.
   *
   * @param status resolution status
   * @param canonicalId canonical identifier when resolved
   * @param aliases known aliases considered during resolution
   * @param mappingVersion mapping-rule version used for the result
   */
  record Resolution(
      Status status, String canonicalId, List<String> aliases, String mappingVersion) {
    /** Resolution status for a source subject. */
    public enum Status {
      /** A single canonical identity was established. */
      RESOLVED,
      /** No canonical identity is available. */
      UNRESOLVED,
      /** Multiple possible identities exist and require review. */
      AMBIGUOUS
    }

    public Resolution {
      aliases = List.copyOf(aliases);
    }
  }
}
