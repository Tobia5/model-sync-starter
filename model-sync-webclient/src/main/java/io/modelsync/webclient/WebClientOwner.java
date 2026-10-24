package io.modelsync.webclient;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import io.modelsync.core.http.HttpSemantics;
import java.net.URI;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Native asynchronous, single HTTP request per invocation. No internal retry. */
public final class WebClientOwner<T> implements AsyncOwner<T> {
  private final WebClient client;
  private final Class<T> type;
  private final Function<ReadRequest, URI> uri;

  public WebClientOwner(WebClient client, Class<T> type, Function<ReadRequest, URI> uri) {
    this.client = client;
    this.type = type;
    this.uri = uri;
  }

  @Override
  public CompletionStage<FetchResult<T>> fetchAsync(ReadRequest request) {
    return Mono.defer(
            () ->
                client
                    .get()
                    .uri(uri.apply(request))
                    .headers(
                        h -> {
                          if (request.validator() != null) h.setIfNoneMatch(request.validator());
                        })
                    .exchangeToMono(
                        response -> {
                          int code = response.statusCode().value();
                          String etag = response.headers().asHttpHeaders().getETag();
                          if (code == 200)
                            return response
                                .bodyToMono(type)
                                .switchIfEmpty(
                                    Mono.error(new Permanent("EMPTY_BODY")))
                                .map(body -> FetchResult.found(body, etag));
                          if (code == 304)
                            return response
                                .releaseBody()
                                .thenReturn(FetchResult.<T>unchanged(etag));
                          if (code == 404)
                            return response.releaseBody().thenReturn(FetchResult.<T>absent());
                          String retry =
                              response.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER);
                          return response
                              .releaseBody()
                              .then(Mono.<FetchResult<T>>error(HttpSemantics.failure(code, retry)));
                        }))
        .toFuture();
  }
}
