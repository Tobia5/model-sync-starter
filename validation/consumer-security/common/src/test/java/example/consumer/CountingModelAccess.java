package example.consumer;

/** Test-only backend for cheap filter/method tests; never represented as persistence. */
public final class CountingModelAccess implements ConsumerModelAccess {
  private final AccessObservation observation;
  public CountingModelAccess(AccessObservation observation) { this.observation = observation; }
  @Override public String execute(String operation, String tenant, String binding, String subject) {
    switch (operation) {
      case "read" -> observation.reads.incrementAndGet();
      case "refresh" -> observation.enqueues.incrementAndGet();
      case "event" -> observation.events.incrementAndGet();
      default -> throw new AssertionError("Unauthorized operation reached backend");
    }
    return tenant + ":" + subject;
  }
}
