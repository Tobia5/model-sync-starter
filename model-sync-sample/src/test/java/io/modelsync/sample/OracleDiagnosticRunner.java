package io.modelsync.sample;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import tools.jackson.databind.json.JsonMapper;

/** Explicit container diagnostics. Passing selected suites is never a full qualification. */
public final class OracleDiagnosticRunner {
  private OracleDiagnosticRunner() {}

  public static void main(String[] args) throws Exception {
    Path root = Path.of(System.getProperty("modelsync.repository.root"));
    var mapper = JsonMapper.builder().build();
    @SuppressWarnings("unchecked") Map<String, Object> report = new LinkedHashMap<>(
        mapper.readValue(Files.readString(root.resolve("target/qm/run-context.json")), Map.class));
    report.put("scope", "DIAGNOSTIC_ONLY");
    report.put("full_qualification", "NOT_RUN");
    List<Map<String, Object>> results = new ArrayList<>();
    report.put("suites", results);
    boolean passed = true;
    for (String name : List.of("io.modelsync.sample.OracleAtomicityIT", "io.modelsync.jdbc.OracleSelectionIT", "io.modelsync.jdbc.OracleScaleIT")) {
      var listener = new SummaryGeneratingListener();
      var launcher = LauncherFactory.create();
      launcher.registerTestExecutionListeners(listener);
      launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass(name)).build());
      var summary = listener.getSummary();
      long expected = name.endsWith("OracleScaleIT") ? 1 : 7;
      boolean success = summary.getTestsFoundCount() == expected
          && summary.getTestsSucceededCount() == expected && summary.getTestsSkippedCount() == 0
          && summary.getTestsFailedCount() == 0 && summary.getContainersFailedCount() == 0;
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("class", name);
      result.put("found", summary.getTestsFoundCount());
      result.put("started", summary.getTestsStartedCount());
      result.put("succeeded", summary.getTestsSucceededCount());
      result.put("failed", summary.getTestsFailedCount());
      result.put("skipped", summary.getTestsSkippedCount());
      result.put("container_failures", summary.getContainersFailedCount());
      result.put("duration_ms", summary.getTimeFinished() - summary.getTimeStarted());
      result.put("status", success ? "PASS" : "FAILED");
      result.put("failures", summary.getFailures().stream().map(f -> Map.of(
          "test", f.getTestIdentifier().getDisplayName(), "exception", f.getException().getClass().getName())).toList());
      results.add(result);
      summary.printTo(new PrintWriter(System.out, true));
      summary.printFailuresTo(new PrintWriter(System.err, true));
      passed &= success;
      report.put("status", passed ? "PASS" : "FAILED");
      Files.writeString(root.resolve("target/qm/oracle-diagnostic.json"), mapper.writeValueAsString(report) + "\n");
      if (!success) break;
    }
    System.exit(passed ? 0 : 1);
  }
}
