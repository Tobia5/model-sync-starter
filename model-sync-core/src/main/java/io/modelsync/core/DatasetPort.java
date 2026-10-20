package io.modelsync.core;

/**
 * Dataset protocol seam for analytical or warehouse-style comparisons.
 *
 * <p>This class is intentionally separate from operational record reconciliation. Dataset manifests
 * compare declared slices, row counts, totals, and digests without treating a remote dataset as a
 * local projection store.
 */
public final class DatasetPort {
  private DatasetPort() {}

  /**
   * Compares two manifests using identity, unresolved-count, row-count, total, and digest evidence.
   *
   * @param expected expected manifest
   * @param actual actual manifest
   * @return comparison outcome
   */
  public static DatasetComparison compare(DatasetManifest expected, DatasetManifest actual) {
    if (!expected.slice().equals(actual.slice())) return DatasetComparison.INCOMPARABLE;
    if (expected.unresolved() != 0 || actual.unresolved() != 0) return DatasetComparison.INCOMPLETE;
    if (expected.rows() != actual.rows()
        || !expected.totals().keySet().equals(actual.totals().keySet()))
      return DatasetComparison.DIFFERENT;
    for (String key : expected.totals().keySet())
      if (expected.totals().get(key).compareTo(actual.totals().get(key)) != 0)
        return DatasetComparison.DIFFERENT;
    if (expected.digest() == null
        || actual.digest() == null
        || expected.digest().isBlank()
        || actual.digest().isBlank()) return DatasetComparison.INCOMPLETE;
    return expected.digest().equals(actual.digest()) ? DatasetComparison.MATCH : DatasetComparison.DIFFERENT;
  }
}
