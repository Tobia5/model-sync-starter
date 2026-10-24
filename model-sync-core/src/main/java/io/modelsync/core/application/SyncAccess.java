package io.modelsync.core.application;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;

import java.util.Objects;
import java.util.Optional;

/**
 * Explicit age-on-visit entry point. Does local database I/O and durable enqueue only; never calls
 * an owner, blocks for reconciliation, or renews validation/retention on a cache hit.
 */
public final class SyncAccess<T> {
  private final ModelSync<T> model;

  public SyncAccess(ModelSync<T> model) {
    this.model = Objects.requireNonNull(model, "model");
  }

  public VisitResult<T> visit(String tenant, String id, VisitPolicy policy) {
    return visit(tenant, id, policy, Priority.HIGH);
  }

  public VisitResult<T> visit(String tenant, String id, VisitPolicy policy, Priority priority) {
    Objects.requireNonNull(policy, "policy");
    Objects.requireNonNull(priority, "priority");
    Optional<LocalModelView<T>> view = model.inspect(tenant, id);
    FreshnessAge age = view.map(LocalModelView::age).orElse(FreshnessAge.UNKNOWN);
    boolean fresh = age == FreshnessAge.FRESH;
    boolean serve = fresh || (age == FreshnessAge.STALE && policy == VisitPolicy.STALE_WHILE_REVALIDATE);
    Optional<Projection<T>> projection = serve ? view.map(LocalModelView::projection) : Optional.empty();
    // Non-forced visits may reuse a successful validation that raced with this read.
    Optional<String> ticket = fresh ? Optional.empty() : model.requestRefresh(tenant, id, priority);
    return new VisitResult<>(projection, age, ticket);
  }
}
