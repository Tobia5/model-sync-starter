package io.modelsync.core;
import io.modelsync.core.domain.*;

import java.time.Instant;

public final class Permanent extends RuntimeException {
  private final String code;

  /**
   * Creates a permanent exception with a stable error code.
   *
   * @param code stable error code
   */
  public Permanent(String code) {
    super(code);
    this.code = SubjectKey.checked(code, "errorCode", 64);
  }

  /**
   * Returns the stable error code.
   *
   * @return error code
   */
  public String code() {
    return code;
  }
}
