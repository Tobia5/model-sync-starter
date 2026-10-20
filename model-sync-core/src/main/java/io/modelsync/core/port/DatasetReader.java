package io.modelsync.core.port;

import io.modelsync.core.*;
import io.modelsync.core.domain.*;

import java.math.BigDecimal;
import java.util.Map;

@FunctionalInterface
public interface DatasetReader {
  /**
   * Inspects a dataset slice without mutating it.
   *
   * @param slice dataset slice identity
   * @return source manifest for the requested slice
   */
  DatasetManifest inspect(DatasetSlice slice);
}
