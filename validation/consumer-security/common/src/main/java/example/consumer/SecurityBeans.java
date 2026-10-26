package example.consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SecurityBeans {
  @Bean ResourcePolicy resourcePolicy() { return new ResourcePolicy(); }
  @Bean AccessObservation accessObservation() { return new AccessObservation(); }
}
