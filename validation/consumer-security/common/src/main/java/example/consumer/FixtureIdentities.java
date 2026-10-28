package example.consumer;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/** Synthetic, process-local test accounts. Never a production identity source. */
public final class FixtureIdentities {
  private static final String PASSWORD = UUID.randomUUID().toString();
  private FixtureIdentities() {}
  public static String password() { return PASSWORD; }
  public static List<UserDetails> users() {
    var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    String encoded = encoder.encode(PASSWORD);
    return List.of(
        User.withUsername("alice").password(encoded).authorities("read:alpha:item:shared", "refresh:alpha:item:shared", "event:alpha:item:shared").build(),
        User.withUsername("bob").password(encoded).authorities("read:beta:item:shared", "refresh:beta:item:shared", "event:beta:item:shared").build(),
        User.withUsername("reader").password(encoded).authorities("read:alpha:item:shared").build(),
        User.withUsername("refresher").password(encoded).authorities("refresh:alpha:item:shared").build(),
        User.withUsername("empty").password(encoded).authorities(List.of()).build());
  }
}
