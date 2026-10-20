package io.modelsync.autoconfigure;

import io.modelsync.core.*;
import io.modelsync.spring.*;
import io.modelsync.testkit.CoreFixture;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class ModelSyncTriggerAutoConfigurationTest {
  private ApplicationContextRunner runner(CoreFixture f) {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ModelSyncAutoConfiguration.class, ModelSyncTriggerAutoConfiguration.class))
        .withBean("customerBinding", ModelBinding.class, () -> f.binding)
        .withBean(WorkStore.class, () -> f.store).withBean(LocalTransactions.class, () -> f.tx)
        .withBean(QuotaGate.class, () -> f.gate).withBean("modelSyncClock", Clock.class, () -> f.clock);
  }
  @Test void disabledRuntimeHasNoTriggerBeans() {
    runner(new CoreFixture()).run(c -> assertThat(c).doesNotHaveBean(SyncTriggers.class));
  }
  @Test void runtimeProvidesExplicitApisButBackgroundAdaptersAreOptIn() {
    runner(new CoreFixture()).withPropertyValues("model-sync.enabled=true").run(c -> {
      assertThat(c).hasSingleBean(SyncTriggers.class).hasSingleBean(SyncJob.class);
      assertThat(c).doesNotHaveBean(SyncEventListener.class).doesNotHaveBean(ScheduledSyncJob.class);
    });
  }
  @Test void enabledEventsCreateDurableIntentNotOwnerIo() {
    var f = new CoreFixture();
    runner(f).withPropertyValues("model-sync.enabled=true", "model-sync.triggers.events-enabled=true").run(c -> {
      c.publishEvent(SyncRequested.changed("customer", "tenant-a", "42"));
      assertThat(f.store.size()).isEqualTo(1);
      assertThat(f.calls.get()).isZero();
    });
  }
  @Test void workerAndCronTogetherFailFast() {
    runner(new CoreFixture()).withPropertyValues("model-sync.enabled=true", "model-sync.worker-enabled=true", "model-sync.triggers.job.enabled=true")
        .run(c -> assertThat(c).hasFailed());
  }
  @Test void invalidCronFailsStartup() {
    runner(new CoreFixture()).withPropertyValues("model-sync.enabled=true", "model-sync.triggers.job.enabled=true", "model-sync.triggers.job.cron=bad")
        .run(c -> assertThat(c).hasFailed());
  }
  @Test void invalidZoneFailsStartup() {
    runner(new CoreFixture()).withPropertyValues("model-sync.enabled=true", "model-sync.triggers.job.enabled=true", "model-sync.triggers.job.zone=invalid-zone")
        .run(c -> assertThat(c).hasFailed());
  }
  @Test void futureCronStartsAndStopsWithContext() {
    runner(new CoreFixture()).withPropertyValues("model-sync.enabled=true", "model-sync.triggers.job.enabled=true", "model-sync.triggers.job.cron=0 0 0 1 1 *")
        .run(c -> assertThat(c.getBean(ScheduledSyncJob.class).isRunning()).isTrue());
  }
}
