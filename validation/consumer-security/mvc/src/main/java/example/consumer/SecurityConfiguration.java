package example.consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {
  @Bean InMemoryUserDetailsManager fixtureUsers() { return new InMemoryUserDetailsManager(FixtureIdentities.users()); }
  @Bean SecurityFilterChain consumerSecurity(HttpSecurity http) throws Exception {
    // CSRF and security headers remain enabled. Basic is test-only over loopback.
    return http.authorizeHttpRequests(a -> a.anyRequest().authenticated()).httpBasic(Customizer.withDefaults()).build();
  }
}
