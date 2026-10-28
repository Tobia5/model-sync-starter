package example.consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "model-sync.enabled", havingValue = "true", matchIfMissing = true)
public class ConsumerFacade {
  private final ConsumerModelAccess access;
  private final AccessObservation observation;
  public ConsumerFacade(ConsumerModelAccess access, AccessObservation observation) { this.access = access; this.observation = observation; }
  @PreAuthorize("@resourcePolicy.allowed(authentication, #operation, #tenant, #binding, #subject)")
  public String execute(String operation, String tenant, String binding, String subject) {
    observation.record(SecurityContextHolder.getContext().getAuthentication().getName(), operation, tenant, binding, subject);
    return access.execute(operation, tenant, binding, subject);
  }
}
