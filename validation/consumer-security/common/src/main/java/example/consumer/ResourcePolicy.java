package example.consumer;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Consumer-owned permission lookup, which never reads protected model data. */
public final class ResourcePolicy {
  public final AtomicInteger lookups = new AtomicInteger();
  public boolean allowed(Authentication identity, String operation, String tenant, String binding, String subject) {
    lookups.incrementAndGet();
    if (identity == null || !identity.isAuthenticated() || identity instanceof AnonymousAuthenticationToken
        || operation == null || !Set.of("read", "refresh", "event").contains(operation)
        || !knownTarget(tenant, binding, subject)) return false;
    String permission = operation + ":" + tenant + ":" + binding + ":" + subject;
    return identity.getAuthorities().stream().anyMatch(a -> permission.equals(a.getAuthority()));
  }
  public static boolean knownTarget(String tenant, String binding, String subject) {
    return tenant != null && subject != null && Set.of("alpha", "beta").contains(tenant)
        && "item".equals(binding) && Set.of("shared", "other").contains(subject);
  }
}
