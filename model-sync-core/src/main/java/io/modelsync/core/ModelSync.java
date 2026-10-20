package io.modelsync.core;

import java.time.Clock;
import java.util.Optional;

/**
 * Injectable per-model facade. Requesting sync returns a durable ticket, not a claim of completion.
 *
 * @param <T> synchronized model type
 */
public final class ModelSync<T> {
  private final ModelBinding<T> binding;
  private final WorkStore store;
  private final LocalTransactions tx;
  private final Clock clock;

  ModelSync(ModelBinding<T> b, WorkStore s, LocalTransactions tx, Clock clock) {
    binding = b;
    store = s;
    this.tx = tx;
    this.clock = clock;
  }

  /**
   * Requests synchronization for a subject without forcing owner revalidation.
   *
   * @param tenant tenant identifier
   * @param id subject identifier
   * @param priority requested work priority
   * @return durable work ticket identifier
   */
  public String request(String tenant, String id, Priority priority) {
    return request(tenant, id, priority, false);
  }

  /**
   * Requests synchronization for a subject.
   *
   * @param tenant tenant identifier
   * @param id subject identifier
   * @param priority requested work priority
   * @param force whether to bypass normal freshness checks
   * @return durable work ticket identifier
   */
  public String request(String tenant, String id, Priority priority, boolean force) {
    WorkKey key = new WorkKey(binding.id(), new SubjectKey(tenant, id));
    return tx.required(() -> store.enqueue(key, priority, force, clock.instant()));
  }

  /** Automatic visit admission; unlike an explicit request, does not revive quarantined work. */
  public Optional<String> requestRefresh(String tenant, String id, Priority priority) {
    WorkKey key = new WorkKey(binding.id(), new SubjectKey(tenant, id));
    return tx.required(() -> store.enqueueRefresh(key, priority, clock.instant()));
  }

  /**
   * Reads the latest known synchronization state for a subject.
   *
   * @param tenant tenant identifier
   * @param id subject identifier
   * @return latest state, or an empty state when no work completed yet
   */
  public SyncState status(String tenant, String id) {
    return tx.required(() -> store.state(new WorkKey(binding.id(), new SubjectKey(tenant, id))));
  }

  /**
   * Local diagnostic view containing projection, state, and age classification.
   *
   * @param projection local projection
   * @param state synchronization state used to assess the projection
   * @param age freshness age classification
   * @param <T> synchronized model type
   */
  public record LocalView<T>(Projection<T> projection, SyncState state, FreshnessPolicy.Age age) {}

  /**
   * Reads a diagnostic local snapshot.
   *
   * <p>The snapshot may be expired, unverified, or different from the owner; callers should inspect
   * the included age and state before serving it.
   *
   * @param tenant tenant identifier
   * @param id subject identifier
   * @return local diagnostic view when a projection exists
   */
  public Optional<LocalView<T>> inspect(String tenant, String id) {
    SubjectKey key = new SubjectKey(tenant, id);
    return tx.required(
        () -> {
          SyncState state = store.state(new WorkKey(binding.id(), key));
          return binding
              .projection()
              .load(key)
              .map(
                  p -> {
                    boolean matches =
                        state.matched()
                            && java.util.Objects.equals(
                                binding.digest(p.value()), state.matchedDigest());
                    FreshnessPolicy.Age age =
                        matches
                            ? binding.freshness().age(state.validatedAt(), clock.instant())
                            : FreshnessPolicy.Age.UNKNOWN;
                    if (p.expired(clock.instant())) age = FreshnessPolicy.Age.EXPIRED;
                    return new LocalView<>(p, state, age);
                  });
        });
  }

  /**
   * Performs a local-only read that permits bounded staleness.
   *
   * @param tenant tenant identifier
   * @param id subject identifier
   * @return projection when it is trusted and younger than {@code maxAge}
   */
  public Optional<Projection<T>> local(String tenant, String id) {
    return inspect(tenant, id)
        .filter(v -> v.age() == FreshnessPolicy.Age.FRESH || v.age() == FreshnessPolicy.Age.STALE)
        .map(LocalView::projection);
  }

  /**
   * Performs a local-only read that requires strict freshness.
   *
   * <p>An empty result is not an instruction to perform hidden owner I/O.
   *
   * @param tenant tenant identifier
   * @param id subject identifier
   * @return projection only when it is classified as fresh
   */
  public Optional<Projection<T>> freshLocal(String tenant, String id) {
    return inspect(tenant, id)
        .filter(v -> v.age() == FreshnessPolicy.Age.FRESH)
        .map(LocalView::projection);
  }

  /**
   * Returns the binding behind this facade.
   *
   * @return model binding
   */
  public ModelBinding<T> binding() {
    return binding;
  }
}
