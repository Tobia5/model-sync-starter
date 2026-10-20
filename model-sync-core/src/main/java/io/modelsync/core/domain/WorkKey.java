package io.modelsync.core.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Durable work identity for a binding and subject.
 *
 * @param binding binding identifier
 * @param subject tenant-scoped subject key
 */
public record WorkKey(String binding, SubjectKey subject) {
  public WorkKey {
    binding = SubjectKey.checked(binding, "binding", 128);
    Objects.requireNonNull(subject);
  }

  /**
   * Computes the durable storage identifier for this work key.
   *
   * <p>Length-prefixing avoids ambiguous concatenation across tenants and bindings.
   *
   * @return stable SHA-256 storage identifier
   */
  public String storageId() {
    try {
      MessageDigest d = MessageDigest.getInstance("SHA-256");
      for (String part : new String[] {binding, subject.tenant(), subject.id()}) {
        byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
        d.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        d.update(bytes);
      }
      return HexFormat.of().formatHex(d.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
