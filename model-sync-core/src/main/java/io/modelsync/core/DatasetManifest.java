package io.modelsync.core;

import java.math.BigDecimal;
import java.util.Map;

public record DatasetManifest(
    DatasetSlice slice, long rows, long unresolved, Map<String, BigDecimal> totals, String digest) {
  public DatasetManifest {
    java.util.Objects.requireNonNull(slice);
    totals = Map.copyOf(totals);
    if (rows < 0 || unresolved < 0) throw new IllegalArgumentException("Negative count");
  }
}
