package io.modelsync.sample;

import io.modelsync.core.*;
import io.modelsync.rest.RestClientOwner;
import io.modelsync.spring.DataOwner;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/** Optional sample configuration that reads customer data from a REST owner. */
@Configuration(proxyBeanMethods = false)
@Profile("rest-owner")
public class RestOwnerConfiguration {
  @Bean
  @DataOwner("customer")
  BlockingOwner<Customer> customerRestOwner(@Value("${owner.base-url}") URI base) {
    if (!"https".equalsIgnoreCase(base.getScheme()))
      throw new IllegalArgumentException("HTTPS owner URL required");
    var transport =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    var factory = new JdkClientHttpRequestFactory(transport);
    factory.setReadTimeout(Duration.ofSeconds(10));
    RestClient client = RestClient.builder().requestFactory(factory).build();
    // Adapt this example URI contract and inject credentials through your approved client
    // configuration.
    return new RestClientOwner<>(
        client,
        Customer.class,
        request ->
            UriComponentsBuilder.fromUri(base)
                .pathSegment("tenants", request.key().tenant(), "customers", request.key().id())
                .build()
                .encode()
                .toUri());
  }
}
