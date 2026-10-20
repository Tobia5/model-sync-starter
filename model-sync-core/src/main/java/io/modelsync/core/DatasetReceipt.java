package io.modelsync.core;

import java.math.BigDecimal;
import java.util.Map;

public record DatasetReceipt(String receiptId, DatasetReceiptStage stage) {
  /** Processing stage reported by the dataset sink. */

}
