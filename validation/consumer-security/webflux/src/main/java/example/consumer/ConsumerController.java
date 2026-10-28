package example.consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@ConditionalOnProperty(name = "model-sync.enabled", havingValue = "true", matchIfMissing = true)
public class ConsumerController {
  private final ConsumerFacade facade;
  public ConsumerController(ConsumerFacade facade) { this.facade = facade; }
  @GetMapping("/csrf") public Mono<CsrfToken> csrf(ServerWebExchange exchange) { return exchange.getAttribute(CsrfToken.class.getName()); }
  @GetMapping("/models/{tenant}/{binding}/{subject}") public Mono<String> read(@PathVariable String tenant, @PathVariable String binding, @PathVariable String subject) {
    return facade.execute("read", tenant, binding, subject);
  }
  @PostMapping("/models/{tenant}/{binding}/{subject}/{operation}") public Mono<String> submit(@PathVariable String tenant, @PathVariable String binding, @PathVariable String subject, @PathVariable String operation) {
    return facade.execute(operation, tenant, binding, subject);
  }
}
