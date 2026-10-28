package example.consumer;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

public final class FacadeProbe {
  private FacadeProbe() {}
  static String call(ConfigurableApplicationContext host,String user,String operation,String tenant,String binding,String subject){
    SecurityContextHolder.clearContext();
    try {
      if(user!=null){var principal=FixtureIdentities.users().stream().filter(u->u.getUsername().equals(user)).findFirst().orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,"",principal.getAuthorities()));}
      return host.getBean(ConsumerFacade.class).execute(operation,tenant,binding,subject);
    }finally{SecurityContextHolder.clearContext();}
  }
}
