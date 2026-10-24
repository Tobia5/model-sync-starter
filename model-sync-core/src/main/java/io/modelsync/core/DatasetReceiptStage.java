package io.modelsync.core;

import java.math.BigDecimal;
import java.util.Map;

public enum DatasetReceiptStage {
  /** Payload was accepted for processing. */
  ACCEPTED,
  /** Payload was applied by the sink. */
  APPLIED,
  /** Payload was independently verified by the sink. */
  VERIFIED
}
