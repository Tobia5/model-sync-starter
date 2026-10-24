package io.modelsync.sample;

import io.modelsync.core.WorkLease;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.springframework.core.env.Environment;

/** Child-to-parent checkpoint; no timing race determines when the parent kills the child. */
final class RecoveryControl {
  private final RecoveryPoint point;
  private final Path directory;
  String value;
  io.modelsync.core.port.BlockingOwner<Customer> ownerOverride;
  OracleFaultProxy commitProxy;
  boolean holdCommitUp;
  WorkLease lease;
  RecoveryControl(Environment env) {
    point = RecoveryPoint.valueOf(env.getProperty("modelsync.recovery.point", "NONE"));
    directory = Path.of(env.getProperty("modelsync.recovery.directory", "target/qm/recovery-parent"));
    value = env.getProperty("modelsync.recovery.value", "before");
  }
  void ownerRead() throws Exception {
    Files.createDirectories(directory);
    Files.writeString(directory.resolve("owner-reads.log"), "synthetic-read\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }
  void checkpoint(RecoveryPoint reached) {
    if (point != reached) return;
    try {
      Path temporary = directory.resolve("checkpoint.tmp");
      FaultEvidence.write(temporary, Map.of("point", reached.name(), "pid", ProcessHandle.current().pid(),
          "work_id", lease.key().storageId(), "token", lease.token(), "generation", lease.generation(), "lease_until_ms", lease.expiresAt().toEpochMilli()));
      Files.move(temporary, directory.resolve("checkpoint.json"), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
      new CountDownLatch(1).await();
      throw new AssertionError("Only process termination may release a crash checkpoint");
    } catch (Exception error) { throw new IllegalStateException("Crash checkpoint failed", error); }
  }
}
