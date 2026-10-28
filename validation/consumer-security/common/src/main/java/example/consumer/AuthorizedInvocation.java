package example.consumer;

/** Detached synthetic audit fact; no credentials, Authentication or entities retained. */
public record AuthorizedInvocation(String principal, String operation, String tenant, String binding, String subject, String thread) {}
