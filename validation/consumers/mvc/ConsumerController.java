package example.consumer;

import io.modelsync.core.Priority;
import io.modelsync.core.SyncRequested;
import io.modelsync.core.application.SyncGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "model-sync.enabled", havingValue = "true")
public class ConsumerController {
  private final SyncGateway gateway;
  private final ApplicationEventPublisher events;
  private final ConsumerObservation observation;
  public ConsumerController(SyncGateway gateway, ApplicationEventPublisher events, ConsumerObservation observation) {
    this.gateway = gateway; this.events = events; this.observation = observation;
  }
  @PostMapping("/request") public String request() {
    observation.requestThreads.add(Thread.currentThread().getName());
    return gateway.model("item", ItemValue.class).request("synthetic", "known-id", Priority.NORMAL, true);
  }
  @PostMapping("/event") public String event() {
    events.publishEvent(SyncRequested.changed("item", "synthetic", "known-id")); return "ENQUEUED";
  }
  @GetMapping("/view") public String view() {
    return gateway.model("item", ItemValue.class).local("synthetic", "known-id").orElseThrow().value().value();
  }
}
