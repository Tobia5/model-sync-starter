package io.modelsync.sample;

import io.modelsync.core.application.SyncCoordinator;
import io.modelsync.core.port.BlockingOwner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Owned independent JVM. Oracle and the parent survive Runtime process termination. */
public final class RecoveryProcess {
  private RecoveryProcess() {}
  static String[] settings(String... args) {
    var values = new java.util.LinkedHashMap<String, String>();
    for (String value : new String[] {"spring.jpa.hibernate.ddl-auto=validate", "spring.main.banner-mode=off",
        "logging.level.root=WARN", "model-sync.enabled=true", "model-sync.worker-enabled=false",
        "model-sync.aop.enabled=false", "model-sync.lease=5s", "model-sync.fetch-timeout=1s",
        "model-sync.retry-base=100ms", "model-sync.quotas.recovery-owner=1ms"}) {
      int separator = value.indexOf('='); values.put(value.substring(0, separator), value.substring(separator + 1));
    }
    for (String argument : args) {
      if (!argument.startsWith("--") || !argument.contains("=")) throw new IllegalArgumentException("Explicit test setting required");
      int separator = argument.indexOf('='); values.put(argument.substring(2, separator), argument.substring(separator + 1));
    }
    return values.entrySet().stream().map(entry -> "--" + entry.getKey() + "=" + entry.getValue()).toArray(String[]::new);
  }
  static org.springframework.context.ConfigurableApplicationContext open(String... args) {
    return new SpringApplicationBuilder(RecoveryConfiguration.class).web(WebApplicationType.NONE)
        .profiles("recovery-oracle").run(settings(args));
  }
  public static void main(String[] args) throws Exception {
    try (var context = open(args)) {
      var control = context.getBean(RecoveryControl.class);
      var coordinator = context.getBean(SyncCoordinator.class);
      var lease = coordinator.claim().orElseThrow(); control.lease = lease;
      control.checkpoint(RecoveryPoint.CLAIM_COMMITTED);
      var prepared = coordinator.prepare(lease).orElseThrow();
      var result = ((BlockingOwner<?>) prepared.binding().owner()).fetch(prepared.request());
      control.checkpoint(RecoveryPoint.OWNER_RESPONDED);
      coordinator.finish(prepared, result);
    }
  }
}
