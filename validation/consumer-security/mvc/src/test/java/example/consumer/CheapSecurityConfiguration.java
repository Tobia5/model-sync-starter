package example.consumer;

import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration(proxyBeanMethods = false)
@Profile("cheap-security")
@EnableWebMvc
@Import({SecurityConfiguration.class, SecurityBeans.class, ConsumerFacade.class, ConsumerController.class})
public class CheapSecurityConfiguration {
  @Bean ConsumerModelAccess fakeModelAccess(AccessObservation observation) { return new CountingModelAccess(observation); }
}
