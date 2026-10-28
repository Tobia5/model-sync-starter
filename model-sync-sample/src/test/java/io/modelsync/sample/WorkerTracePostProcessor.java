package io.modelsync.sample;

import io.modelsync.core.application.SyncCoordinator;
import io.modelsync.jdbc.JdbcWorkStore;
import org.springframework.beans.factory.config.BeanPostProcessor;

final class WorkerTracePostProcessor implements BeanPostProcessor {
  private final WorkerTrace trace;
  WorkerTracePostProcessor(WorkerTrace trace) { this.trace = trace; }
  public Object postProcessAfterInitialization(Object bean, String name) {
    if (!trace.enabled()) return bean;
    if (bean instanceof SyncCoordinator) return trace.coordinator(bean);
    if (bean instanceof JdbcWorkStore) return trace.spy(bean, "store");
    if (bean instanceof WorkerQuota) return trace.spy(bean, "quota");
    return bean;
  }
}
