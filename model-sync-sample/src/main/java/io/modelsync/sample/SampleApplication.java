package io.modelsync.sample;

import io.modelsync.core.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/** Executable Spring Boot sample for the Model Sync starter. */
@SpringBootApplication
public class SampleApplication {
  public static void main(String[] args) {
    try (var context = SpringApplication.run(SampleApplication.class, args)) {
      // Deliberately a one-shot CLI sample, not a production web application.
    }
  }

  @Bean
  @Profile("demo")
  @ConditionalOnProperty(name = "sample.run-demo", havingValue = "true", matchIfMissing = true)
  CommandLineRunner demo(
      ModelSync<Customer> customers, SyncCoordinator coordinator, CustomerService service) {
    return args -> {
      String ticket = customers.request("demo", "42", Priority.HIGH, true);
      coordinator.runOne();
      System.out.println(
          "Ticket: " + ticket + "; outcome: " + customers.status("demo", "42").outcome());
      service.requestReview("demo", "42");
      System.out.println("Review mutation and follow-up request committed together.");
    };
  }
}
