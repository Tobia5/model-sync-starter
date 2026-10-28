package example.consumer;

/** Trusted internal adapter capability. External callers use the secured facade. */
public interface ConsumerModelAccess {
  String execute(String operation, String tenant, String binding, String subject);
}
