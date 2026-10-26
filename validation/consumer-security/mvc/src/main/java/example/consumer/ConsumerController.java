package example.consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "model-sync.enabled", havingValue = "true", matchIfMissing = true)
public class ConsumerController {
  private final ConsumerFacade facade;
  public ConsumerController(ConsumerFacade facade) { this.facade = facade; }
  @GetMapping("/csrf") public CsrfToken csrf(CsrfToken token) { return token; }
  @GetMapping("/models/{tenant}/{binding}/{subject}") public String read(@PathVariable String tenant, @PathVariable String binding, @PathVariable String subject) {
    return facade.execute("read", tenant, binding, subject);
  }
  @PostMapping("/models/{tenant}/{binding}/{subject}/{operation}") public String submit(@PathVariable String tenant, @PathVariable String binding, @PathVariable String subject, @PathVariable String operation) {
    return facade.execute(operation, tenant, binding, subject);
  }
}
