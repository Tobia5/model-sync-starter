package io.modelsync.core;

import java.math.BigDecimal;
import java.util.Map;

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
   * Stable identity for one dataset partition.
   *
   * @param dataset dataset name
   * @param tenant tenant identifier
   * @param snapshot snapshot or extract identifier
   * @param partition partition identifier
   * @param mappingVersion mapping version used to produce the slice
   * @param grain semantic aggregation grain
   */
  public record Slice(
      String dataset,
      String tenant,
      String snapshot,
      String partition,
      String mappingVersion,
      String grain) {
    public Slice {
      for (String s : new String[] {dataset, tenant, snapshot, partition, mappingVersion, grain})
        if (s == null || s.isBlank())
          throw new IllegalArgumentException("Dataset identity fields must be explicit");
    }
  }

  /**
   * Verifiable summary of a dataset slice.
   *
   * @param slice slice identity
   * @param rows number of rows represented by the manifest
   * @param unresolved unresolved or unmapped source rows
   * @param totals named numeric control totals
   * @param digest stable digest over the slice payload
   */
  public record Manifest(
      Slice slice, long rows, long unresolved, Map<String, BigDecimal> totals, String digest) {
    public Manifest {
      java.util.Objects.requireNonNull(slice);
      totals = Map.copyOf(totals);
      if (rows < 0 || unresolved < 0) throw new IllegalArgumentException("Negative count");
    }
  }

  /** Reads a manifest for a dataset slice. */
  @FunctionalInterface
  public interface Reader {
    /**
     * Inspects a dataset slice without mutating it.
     *
     * @param slice dataset slice identity
     * @return source manifest for the requested slice
     */
    Manifest inspect(Slice slice);
  }

  /** Publishes dataset payloads to an analytical destination. */
  @FunctionalInterface
  public interface Sink {
    /**
     * Publishes a payload using an idempotency key chosen by the caller.
     *
     * @param slice dataset slice identity
     * @param idempotencyKey caller-stable publication key
     * @param payload serialized dataset payload
     * @return publication receipt
     */
    Receipt publish(Slice slice, String idempotencyKey, byte[] payload);
  }

  /**
   * Receipt returned by a dataset sink.
   *
   * @param receiptId sink-provided receipt identifier
   * @param stage sink processing stage represented by the receipt
   */
  public record Receipt(String receiptId, Stage stage) {
    /** Processing stage reported by the dataset sink. */
    public enum Stage {
      /** Payload was accepted for processing. */
      ACCEPTED,
      /** Payload was applied by the sink. */
      APPLIED,
      /** Payload was independently verified by the sink. */
      VERIFIED
    }
  }

  /** Outcome of comparing two dataset manifests. */
  public enum Comparison {
    /** Manifests describe the same resolved dataset content. */
    MATCH,
    /** Manifests are comparable and differ. */
    DIFFERENT,
    /** Manifests cannot establish equality because data or digest evidence is incomplete. */
    INCOMPLETE,
    /** Manifests describe different dataset identities. */
    INCOMPARABLE
  }

  /**
   * Compares two manifests using identity, unresolved-count, row-count, total, and digest evidence.
   *
   * @param expected expected manifest
   * @param actual actual manifest
   * @return comparison outcome
   */
  public static Comparison compare(Manifest expected, Manifest actual) {
    if (!expected.slice().equals(actual.slice())) return Comparison.INCOMPARABLE;
    if (expected.unresolved() != 0 || actual.unresolved() != 0) return Comparison.INCOMPLETE;
    if (expected.rows() != actual.rows()
        || !expected.totals().keySet().equals(actual.totals().keySet()))
      return Comparison.DIFFERENT;
    for (String key : expected.totals().keySet())
      if (expected.totals().get(key).compareTo(actual.totals().get(key)) != 0)
        return Comparison.DIFFERENT;
    if (expected.digest() == null
        || actual.digest() == null
        || expected.digest().isBlank()
        || actual.digest().isBlank()) return Comparison.INCOMPLETE;
    return expected.digest().equals(actual.digest()) ? Comparison.MATCH : Comparison.DIFFERENT;
  }
}
