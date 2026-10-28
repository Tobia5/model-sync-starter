package example.consumer;

import io.modelsync.core.SyncRequested;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Observes actual application event publication, separately from facade entry. */
@Component
public final class EventWitness {
  public final AtomicInteger published = new AtomicInteger();
  @EventListener public void observe(SyncRequested event) { published.incrementAndGet(); }
}
