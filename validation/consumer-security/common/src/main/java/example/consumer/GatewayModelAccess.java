package example.consumer;

import io.modelsync.core.Priority;
import io.modelsync.core.SyncRequested;
import io.modelsync.core.application.SyncGateway;
import org.springframework.context.ApplicationEventPublisher;

/** Raw gateway and event publisher remain inside the trusted application boundary. */
public final class GatewayModelAccess implements ConsumerModelAccess {
  private final SyncGateway gateway;
  private final ApplicationEventPublisher publisher;
  private final AccessObservation observation;
  public GatewayModelAccess(SyncGateway gateway, ApplicationEventPublisher publisher, AccessObservation observation) {
    this.gateway = gateway; this.publisher = publisher; this.observation = observation;
  }
  @Override public String execute(String operation, String tenant, String binding, String subject) {
    var model = gateway.model(binding, ItemValue.class);
    return switch (operation) {
      case "read" -> { observation.reads.incrementAndGet(); yield model.local(tenant, subject).map(p -> p.value().value()).orElse("MISSING"); }
      case "refresh" -> { observation.enqueues.incrementAndGet(); yield model.request(tenant, subject, Priority.NORMAL, true); }
      case "event" -> { observation.events.incrementAndGet(); publisher.publishEvent(SyncRequested.changed(binding, tenant, subject)); yield "ENQUEUED"; }
      default -> throw new org.springframework.security.access.AccessDeniedException("Unsupported operation");
    };
  }
}
