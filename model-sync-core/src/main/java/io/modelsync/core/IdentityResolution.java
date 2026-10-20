package io.modelsync.core;

import java.util.List;

public record IdentityResolution(
    IdentityResolutionStatus status, String canonicalId, List<String> aliases, String mappingVersion) {
  /** IdentityResolution status for a source subject. */

  public IdentityResolution {
    aliases = List.copyOf(aliases);
  }
}
