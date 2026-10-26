package example.consumer;

import org.springframework.context.annotation.*;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration(proxyBeanMethods = false)
@Profile("cheap-security")
@EnableWebFlux
@Import({SecurityConfiguration.class, SecurityBeans.class, ConsumerFacade.class, ConsumerController.class})
public class CheapSecurityConfiguration {
  @Bean ConsumerModelAccess fakeModelAccess(AccessObservation observation) { return new CountingModelAccess(observation); }
  @Bean(name = "modelSyncPersistenceScheduler", destroyMethod = "dispose") Scheduler testPersistenceScheduler() {
    return Schedulers.newBoundedElastic(2, 16, "consumer-security-test");
  }
}
