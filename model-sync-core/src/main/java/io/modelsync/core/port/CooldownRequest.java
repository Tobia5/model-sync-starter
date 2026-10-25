package io.modelsync.core.port;

import java.time.Instant;
import java.util.Objects;

/** One provider outcome. Retries retain its identity and absolute deadline. */
public record CooldownRequest(String operationId, String group, Instant until) {
  public CooldownRequest {
    Objects.requireNonNull(operationId); Objects.requireNonNull(group); Objects.requireNonNull(until);
    if (!operationId.matches("[A-Za-z0-9-]{1,64}") || group.isBlank() || group.length() > 128)
      throw new IllegalArgumentException("Invalid cooldown identity");
    long millis=until.toEpochMilli();
    Instant floor=Instant.ofEpochMilli(millis);
    until=until.equals(floor)?floor:Instant.ofEpochMilli(Math.addExact(millis,1));
  }
}
