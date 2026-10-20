package io.modelsync.core;

import java.util.List;

/**
 * Optional owner capability for enumerating externally known subjects.
 *
 * <p>When this capability is absent, Model Sync can reconcile only subjects that the application
 * has already requested or seeded into the work ledger.
 */
public interface BoundaryInventory {
  /**
   * Lists model descriptors exposed by the owner boundary.
   *
   * @return immutable or caller-owned model descriptor list
   */
  List<ModelDescriptor> models();

  /**
   * Enumerates subject identifiers for one model and tenant.
   *
   * @param model owner model identifier
   * @param tenant tenant identifier
   * @param cursor provider cursor from a previous page, or {@code null} for the first page
   * @param limit requested maximum number of subject identifiers
   * @return page of subject identifiers and continuation metadata
   */
  Page enumerate(String model, String tenant, String cursor, int limit);

  /**
   * Describes a source-visible model and its schema version.
   *
   * @param id model identifier
   * @param schemaVersion provider schema or mapping version
   */
  record ModelDescriptor(String id, String schemaVersion) {}

  /**
   * Paged source-inventory response.
   *
   * @param snapshotId provider snapshot or export identifier
   * @param subjectIds subject identifiers in this page
   * @param nextCursor continuation cursor, or {@code null} when no next page exists
   * @param complete whether the page completes the snapshot traversal
   */
  record Page(String snapshotId, List<String> subjectIds, String nextCursor, boolean complete) {
    public Page {
      subjectIds = List.copyOf(subjectIds);
    }
  }
}
