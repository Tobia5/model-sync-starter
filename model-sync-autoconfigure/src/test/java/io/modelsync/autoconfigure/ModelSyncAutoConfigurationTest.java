package io.modelsync.autoconfigure;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelsync.core.*;
import io.modelsync.testkit.*;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ModelSyncAutoConfigurationTest {
  private ApplicationContextRunner runner(CoreFixture f) {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ModelSyncAutoConfiguration.class))
        .withBean("customerBinding", ModelBinding.class, () -> f.binding)
        .withBean(WorkStore.class, () -> f.store)
        .withBean(LocalTransactions.class, () -> f.tx)
        .withBean(QuotaGate.class, () -> f.gate)
        .withBean("modelSyncClock", Clock.class, () -> f.clock);
  }

  @Test
  void disabledByDefault() {
    runner(new CoreFixture()).run(c -> assertThat(c).doesNotHaveBean(SyncCoordinator.class));
  }

  @Test
  void customInfrastructureBacksOffDefaults() {
    var f = new CoreFixture();
    runner(f)
        .withPropertyValues("model-sync.enabled=true")
        .run(
            c -> {
              assertThat(c).hasSingleBean(SyncCoordinator.class);
              assertThat(c.getBean(WorkStore.class)).isSameAs(f.store);
              assertThat(c.getBean(LocalTransactions.class)).isSameAs(f.tx);
            });
  }

  @Test
  void invalidLeaseFailsStartup() {
    runner(new CoreFixture())
        .withPropertyValues(
            "model-sync.enabled=true", "model-sync.lease=10s", "model-sync.fetch-timeout=15s")
        .run(c -> assertThat(c).hasFailed());
  }

  @Test
  void noBindingsFailsStartup() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ModelSyncAutoConfiguration.class))
        .withPropertyValues("model-sync.enabled=true")
        .run(c -> assertThat(c).hasFailed());
  }

  @Test
  void userPriorityPolicyCanReplaceOnlyThatDefault() {
    var custom = new WeightedPriorities(1, 1, 1, 1);
    runner(new CoreFixture())
        .withBean(WeightedPriorities.class, () -> custom)
        .withPropertyValues("model-sync.enabled=true")
        .run(
            c -> {
              assertThat(c).hasSingleBean(WeightedPriorities.class);
              assertThat(c.getBean(WeightedPriorities.class)).isSameAs(custom);
            });
  }

  @Test void invalidWorkerPacingFailsStartup() {
    runner(new CoreFixture()).withPropertyValues("model-sync.enabled=true", "model-sync.productive-interval=0ms")
        .run(c -> assertThat(c).hasFailed());
    runner(new CoreFixture()).withPropertyValues("model-sync.enabled=true", "model-sync.max-idle-interval=100ms")
        .run(c -> assertThat(c).hasFailed());
  }
}
