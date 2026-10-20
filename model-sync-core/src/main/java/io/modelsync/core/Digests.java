package io.modelsync.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

/** Utility methods for deterministic cryptographic digests used by model bindings. */
public final class Digests {
  private Digests() {}

  /**
   * Computes a SHA-256 hex digest over length-prefixed UTF-8 fields.
   *
   * <p>Length prefixing preserves {@code null} versus empty strings and avoids delimiter
   * collisions. Callers still own semantic normalization and field order.
   *
   * @param fields ordered fields to hash
   * @return lowercase hexadecimal SHA-256 digest
   */
  public static String sha256Fields(String... fields) {
    try {
      MessageDigest hash = MessageDigest.getInstance("SHA-256");
      for (String field : fields) {
        if (field == null) {
          hash.update(ByteBuffer.allocate(4).putInt(-1).array());
          continue;
        }
        byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
        hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        hash.update(bytes);
      }
      return HexFormat.of().formatHex(hash.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
