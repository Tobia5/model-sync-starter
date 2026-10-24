package io.modelsync.core.port;

import io.modelsync.core.*;
import io.modelsync.core.domain.*;

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
  IdentityResolution resolve(String sourceSystem, SubjectKey sourceKey);

}
