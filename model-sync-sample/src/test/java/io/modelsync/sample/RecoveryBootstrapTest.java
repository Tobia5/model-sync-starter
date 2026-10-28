package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;

/** Load the real demo YAML and prove that it cannot overwrite the isolated fault-host policy. */
@Configuration(proxyBeanMethods = false)
class RecoveryBootstrapTest {
  @Test void actualYamlCannotEnableCreateDropOrOverrideFaultBudgets() {
    try (var host = new SpringApplicationBuilder(RecoveryBootstrapTest.class).web(WebApplicationType.NONE)
        .run(RecoveryProcess.settings("--model-sync.fetch-timeout=300ms"))) {
      var environment = host.getEnvironment();
      assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
      assertEquals("5s", environment.getProperty("model-sync.lease"));
      assertEquals("300ms", environment.getProperty("model-sync.fetch-timeout"));
      assertEquals("false", environment.getProperty("model-sync.aop.enabled"));
    }
    assertThrows(IllegalStateException.class, () -> new RecoveryConfiguration().recoveryDataSource(new StandardEnvironment()));
  }
}
