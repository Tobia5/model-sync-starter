package io.modelsync.sample;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** The only forcibly terminated process is the Process object created here. */
final class OwnedRecoveryJvm implements AutoCloseable {
  final Process process;
  final Path directory;
  OwnedRecoveryJvm(Path directory, RecoveryPoint point, String value) throws Exception {
    this.directory = directory; Files.createDirectories(directory);
    List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-Xmx384m", "-Dmodelsync.repository.root=" + FaultEvidence.root(),
        "-Dmodelsync.qualification.target=" + System.getProperty("modelsync.qualification.target", "NONE"),
        "-cp", System.getProperty("java.class.path"),
        RecoveryProcess.class.getName(), "--modelsync.recovery.point=" + point,
        "--modelsync.recovery.directory=" + directory.toAbsolutePath(), "--modelsync.recovery.value=" + value));
    process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(directory.resolve("child.log").toFile()).start();
  }
  void kill() throws Exception {
    process.destroyForcibly();
    if (!process.waitFor(10, TimeUnit.SECONDS) || process.isAlive()) throw new AssertionError("Owned child did not terminate");
  }
  @Override public void close() throws Exception { if (process.isAlive()) kill(); }
}
