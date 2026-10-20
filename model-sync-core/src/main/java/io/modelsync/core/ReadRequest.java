package io.modelsync.core;

import java.util.Objects;

/**
 * Request sent to an authoritative owner.
 *
 * @param key subject key to read
 * @param validator optional owner validator from the last accepted response
 */
public record ReadRequest(SubjectKey key, String validator) {
  public ReadRequest {
    Objects.requireNonNull(key);
  }
}
