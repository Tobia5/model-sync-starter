package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class OracleMeasurementsTest {
  @Test void populationBucketsIncludeEqualityAndExcludeOtherBindings() {
    var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
        + ";DB_CLOSE_DELAY=-1", "sa", ""));
    jdbc.execute("create table MS_WORK (binding_id varchar(64),work_status varchar(16),next_at_ms bigint)");
    jdbc.update("insert into MS_WORK values ('fixture','WAITING',9),('fixture','WAITING',10),"
        + "('fixture','WAITING',11),('fixture','LEASED',0),('other','WAITING',0)");
    var groups = OracleMeasurements.population(jdbc, "fixture", 10).stream().collect(
        java.util.stream.Collectors.toMap(row -> row.get("WORK_STATUS") + "/" + row.get("SCHEDULE"),
            row -> ((Number) row.get("ROWS_COUNT")).longValue()));
    assertEquals(Map.of("WAITING/DUE", 2L, "WAITING/FUTURE", 1L, "LEASED/DUE", 1L), groups);
  }

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
