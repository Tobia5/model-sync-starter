package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class OracleMeasurementsTest {
  @Test void cursorReloadWithResetOrRetainedCumulativeCountsIsUnattributable() {
    Map<String, Object> old = Map.of("SQL_ID", "claim", "CHILD_NUMBER", 0, "CHILD_ADDRESS", "A0",
        "LAST_LOAD_TIME", "2026-10-20/22:14:00", "LOADS", 1, "EXECUTIONS", 15);
    Map<String, Object> reloaded = Map.of("SQL_ID", "claim", "CHILD_NUMBER", 0, "CHILD_ADDRESS", "B0",
        "LAST_LOAD_TIME", "2026-10-20/22:17:00", "LOADS", 1, "EXECUTIONS", 3);
    var fresh = OracleMeasurements.cursorIntervals(List.of(old), List.of(reloaded));
    assertEquals(1, fresh.size());
    assertNull(fresh.getFirst().get("window_executions"));
    assertEquals("LOAD_IDENTITY_CHANGED", fresh.getFirst().get("reason"));
    var retained = new java.util.LinkedHashMap<>(old);
    retained.put("LOADS", 2);
    retained.put("EXECUTIONS", 18);
    var interval = OracleMeasurements.cursorIntervals(List.of(old), List.of(retained)).getFirst();
    assertEquals("UNATTRIBUTABLE", interval.get("attribution"));
    assertNull(interval.get("window_executions"));
    assertSame(old, interval.get("before"));
    assertSame(retained, interval.get("after"));
  }

  @Test void sameLoadedCursorHasDeltasOnlyForContinuousMonotonicCounters() {
    var old = cursor(15);
    assertEquals(0L, OracleMeasurements.cursorIntervals(List.of(old), List.of(old)).getFirst().get("window_executions"));
    var invalid = new java.util.LinkedHashMap<>(old);
    invalid.put("EXECUTIONS", 3);
    var reset = OracleMeasurements.cursorIntervals(List.of(old), List.of(invalid)).getFirst();
    assertEquals("COUNTER_RESET", reset.get("reason"));
    assertNull(reset.get("counter_deltas"));
    var advanced = new java.util.LinkedHashMap<>(old);
    advanced.put("EXECUTIONS", 18);
    var valid = OracleMeasurements.cursorIntervals(List.of(old), List.of(advanced)).getFirst();
    assertEquals("VALID", valid.get("attribution"));
    assertEquals(3L, valid.get("window_executions"));
    advanced.put("BUFFER_GETS", 2L);
    old.put("BUFFER_GETS", 20L);
    assertEquals("COUNTER_RESET", OracleMeasurements.cursorIntervals(List.of(old), List.of(advanced)).getFirst().get("reason"));
  }

  @Test void absentBeforeAndDisappearingChildrenNeverBecomeZeroBasedDeltas() {
    var old = cursor(15);
    var appeared = OracleMeasurements.cursorIntervals(List.of(), List.of(old)).getFirst();
    assertEquals("MISSING_BEFORE", appeared.get("reason"));
    assertNull(appeared.get("window_executions"));
    var disappeared = OracleMeasurements.cursorIntervals(List.of(old), List.of()).getFirst();
    assertEquals("DISAPPEARED_CHILD", disappeared.get("reason"));
    assertSame(old, disappeared.get("before"));
    assertNull(disappeared.get("after"));
    assertNull(disappeared.get("window_executions"));
  }

  private static java.util.LinkedHashMap<String, Object> cursor(long executions) {
    return new java.util.LinkedHashMap<>(Map.of("SQL_ID", "claim", "CHILD_NUMBER", 0,
        "CHILD_ADDRESS", "A0", "LAST_LOAD_TIME", "2026-10-20/22:14:00", "LOADS", 1, "EXECUTIONS", executions));
  }

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
