package io.modelsync.core.application;
import io.modelsync.core.port.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.DoubleSupplier;

/** One responsibility of the synchronization application service. */
final class OwnerCallExecutor {
  private final SyncOptions options;
  OwnerCallExecutor(SyncOptions options) { this.options=options; }
  FetchResult<?> fetchBlocking(PreparedSync<?> p) throws Exception {
    if (p.binding().owner() instanceof BlockingOwner<?> owner) return owner.fetch(p.request());
    AsyncOwner<?> owner = (AsyncOwner<?>) p.binding().owner();
    CompletableFuture<? extends FetchResult<?>> future =
        owner.fetchAsync(p.request()).toCompletableFuture();
    try {
      return future.get(options.fetchTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException | InterruptedException e) {
      future.cancel(true);
      throw e;
    }
  }
}
