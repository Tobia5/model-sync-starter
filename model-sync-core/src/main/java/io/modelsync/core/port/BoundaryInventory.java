package io.modelsync.core.port;

import io.modelsync.core.*;
import io.modelsync.core.domain.*;

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
  List<BoundaryModelDescriptor> models();

  /**
   * Enumerates subject identifiers for one model and tenant.
   *
   * @param model owner model identifier
   * @param tenant tenant identifier
   * @param cursor provider cursor from a previous page, or {@code null} for the first page
   * @param limit requested maximum number of subject identifiers
   * @return page of subject identifiers and continuation metadata
   */
  BoundaryPage enumerate(String model, String tenant, String cursor, int limit);

}
