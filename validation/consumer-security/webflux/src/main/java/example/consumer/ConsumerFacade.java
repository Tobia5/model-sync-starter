package example.consumer;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Service
@ConditionalOnProperty(name = "model-sync.enabled", havingValue = "true", matchIfMissing = true)
public class ConsumerFacade {
  private final ConsumerModelAccess access;
  private final AccessObservation observation;
  private final Scheduler persistence;
  public ConsumerFacade(ConsumerModelAccess access, AccessObservation observation,
      @Qualifier("modelSyncPersistenceScheduler") Scheduler persistence) {
    this.access = access; this.observation = observation; this.persistence = persistence;
  }
  @PreAuthorize("@resourcePolicy.allowed(authentication, #operation, #tenant, #binding, #subject)")
  public Mono<String> execute(String operation, String tenant, String binding, String subject) {
    return ReactiveSecurityContextHolder.getContext().flatMap(context -> {
      // Only the detached name crosses this callable; no JPA entity or thread-local assumption.
      String principal = context.getAuthentication().getName();
      return Mono.fromCallable(() -> {
        observation.record(principal, operation, tenant, binding, subject);
        return access.execute(operation, tenant, binding, subject);
      }).subscribeOn(persistence);
    }).switchIfEmpty(Mono.error(new org.springframework.security.access.AccessDeniedException("Identity absent")));
  }
}
