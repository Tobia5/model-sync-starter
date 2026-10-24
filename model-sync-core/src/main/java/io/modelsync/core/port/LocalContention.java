package io.modelsync.core.port;

/**
 * A local persistence operation was refused because another transaction owns a required lock.
 * The enclosing attempt must roll back before a separate failure outcome is persisted.
 * This is neither owner failure nor proof that every persistence path has a wait bound.
 */
public final class LocalContention extends RuntimeException {
  /**
   * @param operation nonsecret description of the refused local operation
   * @param cause detached diagnostic failure, without managed entities, sessions or connections
   */
  public LocalContention(String operation, Throwable cause) {
    super("Local persistence contention: " + java.util.Objects.requireNonNull(operation), cause);
  }
}
