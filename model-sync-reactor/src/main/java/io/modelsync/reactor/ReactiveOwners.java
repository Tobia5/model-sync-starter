package io.modelsync.reactor;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import java.util.function.Function;
import reactor.core.publisher.Mono;

/** Keeps Reactor out of the core SPI while preserving lazy native asynchronous I/O. */
public final class ReactiveOwners {
  private ReactiveOwners() {}

  public static <T> AsyncOwner<T> from(Function<ReadRequest, Mono<FetchResult<T>>> function) {
    return request -> Mono.defer(() -> function.apply(request)).toFuture();
  }
}
