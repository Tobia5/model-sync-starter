package io.modelsync.jdbc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Test-only observations of the dedicated runtime's stable session population and SQL cursors. */
final class OracleMeasurements {
  private OracleMeasurements() {}

  static List<Map<String, Object>> population(JdbcTemplate jdbc, String binding, long now) {
    // Oracle does not consider two positional binds the same GROUP BY expression, even when
    // their values agree. Compute the bucket once, then group its derived column.
    return jdbc.queryForList("select work_status,schedule,count(*) rows_count from"
        + " (select work_status,case when next_at_ms<=? then 'DUE' else 'FUTURE' end schedule"
        + " from MS_WORK where binding_id=?) group by work_status,schedule", now, binding);
  }

  static Map<String, Long> statistics(JdbcTemplate jdbc) {
    Map<String, Long> result = new LinkedHashMap<>();
    jdbc.query("select n.name,sum(s.value) value from v$sesstat s join v$statname n"
        + " on n.statistic#=s.statistic# join v$session x on x.sid=s.sid where x.username=user"
        + " and n.name in ('db block gets','consistent gets','physical reads','execute count',"
        + "'parse count (total)','redo size','user commits','user rollbacks','CPU used by this session')"
        + " group by n.name order by n.name", r -> { result.put(r.getString(1), r.getLong(2)); });
    return result;
  }

  static Map<String, Long> waits(JdbcTemplate jdbc) {
    Map<String, Long> result = new LinkedHashMap<>();
    jdbc.query("select e.event,sum(e.total_waits),sum(e.time_waited_micro) from v$session_event e"
        + " join v$session s on s.sid=e.sid where s.username=user and e.wait_class<>'Idle'"
        + " group by e.event order by e.event", r -> {
          result.put(r.getString(1) + ":waits", r.getLong(2));
          result.put(r.getString(1) + ":microseconds", r.getLong(3));
        });
    return result;
  }

  static Map<String, Long> delta(Map<String, Long> before, Map<String, Long> after) {
    Map<String, Long> result = new LinkedHashMap<>();
    after.forEach((name, value) -> result.put(name, value - before.getOrDefault(name, 0L)));
    before.forEach((name, value) -> {
      if (!after.containsKey(name)) throw new IllegalStateException("Oracle session population lost counter " + name);
    });
    if (result.values().stream().anyMatch(value -> value < 0))
      throw new IllegalStateException("Oracle session population/counters regressed");
    return result;
  }

  static List<Map<String, Object>> sql(JdbcTemplate jdbc) {
    return jdbc.queryForList("select sql_id,child_number,plan_hash_value,executions,fetches,rows_processed,"
        + "buffer_gets,disk_reads,elapsed_time,cpu_time,application_wait_time,concurrency_wait_time,"
        + "user_io_wait_time,sql_text from v$sql where parsing_schema_name=user and ("
        + "sql_text like 'select * from MS_WORK where priority=%' or sql_text like 'update MS_WORK set%'"
        + " or sql_text like 'select * from MS_WORK where work_id=%' or sql_text like 'select * from MS_STATE%'"
        + " or sql_text like 'update MS_STATE set%' or sql_text like 'insert into MS_STATE%'"
        + " or sql_text like 'select%MS_QUOTA%for update%' or sql_text like 'update MS_QUOTA set%'"
        + " or sql_text like 'insert into MS_TEST_PROJECTION%' or sql_text like 'update MS_TEST_PROJECTION%')"
        + " order by sql_id,child_number");
  }

  static Map<String, Object> snapshot(JdbcTemplate jdbc) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("session_statistics", statistics(jdbc));
    result.put("nonidle_session_waits", waits(jdbc));
    result.put("sql_cursors", sql(jdbc));
    return result;
  }

  static Map<String, Object> difference(Map<String, Object> before, Map<String, Object> after) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (String key : List.of("session_statistics", "nonidle_session_waits")) {
      @SuppressWarnings("unchecked") var first = (Map<String, Long>) before.get(key);
      @SuppressWarnings("unchecked") var last = (Map<String, Long>) after.get(key);
      result.put(key, delta(first, last));
    }
    // Keep cursor identities and all raw values; consumers subtract by (SQL_ID, CHILD_NUMBER).
    result.put("sql_cursors_before", before.get("sql_cursors"));
    result.put("sql_cursors_after", after.get("sql_cursors"));
    result.put("observation_scope", "all runtime-user sessions; stable pool; includes snapshot query overhead");
    return result;
  }

  static List<Map<String, Object>> claimPlans(JdbcTemplate jdbc, List<Map<String, Object>> before) {
    Map<String, Long> previous = new LinkedHashMap<>();
    for (var cursor : before) previous.put(cursor.get("SQL_ID") + ":" + cursor.get("CHILD_NUMBER"),
        ((Number) cursor.get("EXECUTIONS")).longValue());
    List<Map<String, Object>> cursors = jdbc.queryForList("select sql_id,child_number,plan_hash_value,"
        + "executions,buffer_gets,disk_reads,rows_processed from v$sql where parsing_schema_name=user"
        + " and sql_text like 'select * from MS_WORK where priority=%' order by sql_id,child_number").stream()
        .filter(cursor -> ((Number) cursor.get("EXECUTIONS")).longValue() > previous.getOrDefault(
            cursor.get("SQL_ID") + ":" + cursor.get("CHILD_NUMBER"), 0L)).toList();
    if (cursors.isEmpty()) throw new IllegalStateException("Actual production claim cursor absent");
    for (var cursor : cursors) {
      var plan = jdbc.queryForList("select plan_table_output from table(dbms_xplan.display_cursor(?,?,'ALLSTATS LAST +PREDICATE +IOSTATS'))",
          String.class, cursor.get("SQL_ID"), cursor.get("CHILD_NUMBER"));
      if (plan.isEmpty() || plan.stream().noneMatch(line -> line.contains("A-Rows"))
          || plan.stream().anyMatch(line -> line.contains("cannot fetch plan")))
        throw new IllegalStateException("Actual claim execution plan unavailable");
      cursor.put("actual_plan", plan);
    }
    return cursors;
  }

  static void write(Path path, Object value) throws Exception {
    Files.createDirectories(path.getParent());
    Files.writeString(path, JsonMapper.builder().build().writeValueAsString(value) + "\n");
  }
}
