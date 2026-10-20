package io.modelsync.reactor;

import io.modelsync.core.*;
import reactor.core.publisher.Mono;
import java.util.function.Function;
/** Keeps Reactor out of the core SPI while preserving lazy native asynchronous I/O. */
public final class ReactiveOwners {
    private ReactiveOwners(){}
    public static <T> AsyncOwner<T> from(Function<ReadRequest,Mono<FetchResult<T>>> function){
        return request->Mono.defer(()->function.apply(request)).toFuture();
    }
}
