package example.consumer;

import io.modelsync.core.Priority;
import io.modelsync.core.SyncRequested;
import io.modelsync.reactor.ReactiveSyncGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@RestController
@ConditionalOnProperty(name = "model-sync.enabled", havingValue = "true")
public class ConsumerController {
  private final ReactiveSyncGateway gateway;
  private final ApplicationEventPublisher events;
  private final Scheduler persistence;
  private final ConsumerObservation observation;
  public ConsumerController(ReactiveSyncGateway gateway, ApplicationEventPublisher events,
      @Qualifier("modelSyncPersistenceScheduler") Scheduler persistence, ConsumerObservation observation) {
    this.gateway = gateway; this.events = events; this.persistence = persistence; this.observation = observation;
  }
  @PostMapping("/request") public Mono<String> request() {
    observation.requestThreads.add(Thread.currentThread().getName());
    return gateway.model("item", ItemValue.class).request("synthetic", "known-id", Priority.NORMAL, true);
  }
  @PostMapping("/event") public Mono<String> event() {
    return Mono.fromCallable(() -> {
      events.publishEvent(SyncRequested.changed("item", "synthetic", "known-id")); return "ENQUEUED";
    }).subscribeOn(persistence);
  }
  @GetMapping("/view") public Mono<String> view() {
    return gateway.model("item", ItemValue.class).local("synthetic", "known-id").map(p -> p.value().value());
  }
}
