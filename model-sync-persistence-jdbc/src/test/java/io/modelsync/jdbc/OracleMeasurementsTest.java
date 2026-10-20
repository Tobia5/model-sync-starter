package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class OracleMeasurementsTest {
  @Test void evidenceEscapesDatabaseTextAndPreservesSeparateCounters(@TempDir Path directory) throws Exception {
    String text = "Oracle \"quoted\" \\ plan\n\t\u0001";
    Path report = directory.resolve("report.json");
    OracleMeasurements.write(report, Map.of("text", text, "consistent gets", 12L, "physical reads", 3L,
        "last_analyzed", Timestamp.valueOf("2026-10-20 12:00:00")));
    var decoded = JsonMapper.builder().build().readTree(Files.readString(report));
    assertEquals(text, decoded.get("text").asString());
    assertEquals(12L, decoded.get("consistent gets").asLong());
    assertEquals(3L, decoded.get("physical reads").asLong());
    assertFalse(decoded.get("last_analyzed").isNull());
  }

  @Test void unstableSessionPopulationCannotBecomePositiveWorkEvidence() {
    assertThrows(IllegalStateException.class, () -> OracleMeasurements.delta(
        Map.of("consistent gets", 100L), Map.of("consistent gets", 90L)));
    assertThrows(IllegalStateException.class, () -> OracleMeasurements.delta(
        Map.of("consistent gets", 100L), Map.of()));
    assertEquals(Map.of("consistent gets", 10L, "physical reads", 2L), OracleMeasurements.delta(
        Map.of("consistent gets", 100L), Map.of("consistent gets", 110L, "physical reads", 2L)));
  }
}
