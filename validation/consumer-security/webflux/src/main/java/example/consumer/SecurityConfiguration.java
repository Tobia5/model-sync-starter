package example.consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfiguration {
  @Bean MapReactiveUserDetailsService fixtureUsers() { return new MapReactiveUserDetailsService(FixtureIdentities.users()); }
  @Bean SecurityWebFilterChain consumerSecurity(ServerHttpSecurity http) {
    // CSRF/BREACH protection and security headers remain at framework defaults.
    return http.authorizeExchange(a -> a.anyExchange().authenticated()).httpBasic(Customizer.withDefaults()).build();
  }
}
