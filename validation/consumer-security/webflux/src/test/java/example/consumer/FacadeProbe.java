package example.consumer;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

public final class FacadeProbe {
  private FacadeProbe() {}
  static String call(ConfigurableApplicationContext host,String user,String operation,String tenant,String binding,String subject){
    var result=host.getBean(ConsumerFacade.class).execute(operation,tenant,binding,subject);
    if(user!=null){var principal=FixtureIdentities.users().stream().filter(u->u.getUsername().equals(user)).findFirst().orElseThrow();
      result=result.contextWrite(ReactiveSecurityContextHolder.withAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,"",principal.getAuthorities())));}
    return result.block(java.time.Duration.ofSeconds(5));
  }
}
