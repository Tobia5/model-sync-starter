package io.modelsync.sample;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Source/run identity is copied into every fault report, including partial failures. */
final class FaultEvidence {
  private FaultEvidence() {}
  static Path root() {
    return Path.of(System.getProperty("modelsync.repository.root", Path.of("..").toAbsolutePath().normalize().toString()));
  }
  static Map<String, Object> report(String scope) throws Exception {
    var result = read(root().resolve("target/qm/run-context.json"));
    result.put("scope", scope); result.put("status", "FAILED");
    return result;
  }
  static Map<String, Object> read(Path file) throws Exception {
    @SuppressWarnings("unchecked") Map<String, Object> value = JsonMapper.builder().build().readValue(Files.readString(file), Map.class);
    return new LinkedHashMap<>(value);
  }
  static void write(Path file, Object value) throws Exception {
    Files.createDirectories(file.getParent());
    Files.writeString(file, JsonMapper.builder().build().writeValueAsString(value) + "\n");
  }
  static void write(String name, Object value) throws Exception {
    write(root().resolve("model-sync-sample/target/qm/" + name), value);
  }
  static void awaitNoPoolThreads(String poolName) throws Exception {
    OracleRecoveryIT.await(() -> Thread.getAllStackTraces().keySet().stream()
        .noneMatch(thread -> thread.isAlive() && thread.getName().startsWith(poolName)), 5000);
  }
}
