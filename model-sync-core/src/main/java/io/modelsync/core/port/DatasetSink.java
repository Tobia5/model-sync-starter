package io.modelsync.core.port;

import io.modelsync.core.*;
import io.modelsync.core.domain.*;

import java.math.BigDecimal;
import java.util.Map;

@FunctionalInterface
public interface DatasetSink {
  /**
   * Publishes a payload using an idempotency key chosen by the caller.
   *
   * @param slice dataset slice identity
   * @param idempotencyKey caller-stable publication key
   * @param payload serialized dataset payload
   * @return publication receipt
   */
  DatasetReceipt publish(DatasetSlice slice, String idempotencyKey, byte[] payload);
}
