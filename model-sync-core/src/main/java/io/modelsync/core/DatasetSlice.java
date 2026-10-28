package io.modelsync.core;

import java.math.BigDecimal;
import java.util.Map;

public record DatasetSlice(
    String dataset,
    String tenant,
    String snapshot,
    String partition,
    String mappingVersion,
    String grain) {
  public DatasetSlice {
    for (String s : new String[] {dataset, tenant, snapshot, partition, mappingVersion, grain})
      if (s == null || s.isBlank())
        throw new IllegalArgumentException("Dataset identity fields must be explicit");
  }
}
