package io.modelsync.rest;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import io.modelsync.core.http.HttpSemantics;
import java.net.URI;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.http.*;
import org.springframework.web.client.*;

/**
 * Single-request blocking adapter. Supply an authenticated client with finite transport timeouts.
 */
public final class RestClientOwner<T> implements BlockingOwner<T> {
  private final RestClient client;
  private final Class<T> type;
  private final Function<ReadRequest, URI> uri;

  public RestClientOwner(RestClient client, Class<T> type, Function<ReadRequest, URI> uri) {
    this.client = Objects.requireNonNull(client);
    this.type = Objects.requireNonNull(type);
    this.uri = Objects.requireNonNull(uri);
  }

  @Override
  public FetchResult<T> fetch(ReadRequest request) {
    try {
      ResponseEntity<T> response =
          client
              .get()
              .uri(uri.apply(request))
              .headers(
                  headers -> {
                    if (request.validator() != null) headers.setIfNoneMatch(request.validator());
                  })
              .retrieve()
              .onStatus(
                  status -> status.is3xxRedirection() && status.value() != 304,
                  (req, res) -> {
                    throw new Permanent("HTTP_REDIRECT");
                  })
              .toEntity(type);
      if (response.getStatusCode().value() == 304)
        return FetchResult.unchanged(response.getHeaders().getETag());
      if (response.getStatusCode().value() != 200)
        throw new Permanent("UNEXPECTED_SUCCESS_STATUS");
      if (response.getBody() == null) throw new Permanent("EMPTY_BODY");
      return FetchResult.found(response.getBody(), response.getHeaders().getETag());
    } catch (RestClientResponseException response) {
      if (response.getStatusCode().value() == 404) return FetchResult.absent();
      String retry =
          response.getResponseHeaders() == null
              ? null
              : response.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
      throw HttpSemantics.failure(response.getStatusCode().value(), retry);
    }
  }
}
