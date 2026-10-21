package io.modelsync.jdbc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
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
        + "'parse count (total)','redo size','user commits','user rollbacks','CPU used by this session',"
        + "'sorts (rows)','sorts (memory)','sorts (disk)')"
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
    return jdbc.queryForList("select sql_id,child_number,rawtohex(child_address) child_address,last_load_time,loads,"
        + "plan_hash_value,executions,fetches,rows_processed,sorts,"
        + "buffer_gets,disk_reads,elapsed_time,cpu_time,application_wait_time,concurrency_wait_time,"
        + "user_io_wait_time,sql_text from v$sql where parsing_schema_name=user and ("
        + "(sql_text like 'select * from MS_WORK where priority=%' or sql_text like 'select /*+ index_asc(w MS_WORK_%' or sql_text like 'select /* modelsync-claim-lock */%') or sql_text like 'update MS_WORK set%'"
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

  @SuppressWarnings("unchecked")
  static Map<String, Long> statisticsFrom(Map<String, Object> snapshot) {
    return (Map<String, Long>) snapshot.get("session_statistics");
  }

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> sqlFrom(Map<String, Object> snapshot) {
    return (List<Map<String, Object>>) snapshot.get("sql_cursors");
  }

  static Map<String, Object> difference(Map<String, Object> before, Map<String, Object> after) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (String key : List.of("session_statistics", "nonidle_session_waits")) {
      @SuppressWarnings("unchecked") var first = (Map<String, Long>) before.get(key);
      @SuppressWarnings("unchecked") var last = (Map<String, Long>) after.get(key);
      result.put(key, delta(first, last));
    }
    // Keep loaded-cursor identities and raw values, including reloads that reset execution counts.
    result.put("sql_cursors_before", before.get("sql_cursors"));
    result.put("sql_cursors_after", after.get("sql_cursors"));
    result.put("cursor_intervals", cursorIntervals(sqlFrom(before), sqlFrom(after)));
    result.put("observation_scope", "all runtime-user sessions; stable pool; includes snapshot query overhead");
    return result;
  }

  static List<Map<String, Object>> claimPlans(JdbcTemplate jdbc, List<Map<String, Object>> before) {
    var current = jdbc.queryForList("select sql_id,child_number,rawtohex(child_address) child_address,last_load_time,loads,plan_hash_value,"
        + "executions,buffer_gets,disk_reads,rows_processed,sql_text from v$sql where parsing_schema_name=user"
        + " and (sql_text like 'select * from MS_WORK where priority=%' or sql_text like 'select /*+ index_asc(w MS_WORK_%' or sql_text like 'select /* modelsync-claim-lock */%') order by sql_id,child_number");
    // Discovery is deliberately separate from attribution. A reloaded cursor may retain old
    // cumulative counts; its LAST plan is retained, explicitly without window attribution.
    List<Map<String, Object>> cursors = new ArrayList<>();
    for (var interval : cursorIntervals(before, current)) {
      @SuppressWarnings("unchecked") var row = (Map<String, Object>) interval.get("after");
      if (row == null || ((Number) row.get("EXECUTIONS")).longValue() == 0) continue;
      if ("VALID".equals(interval.get("attribution"))
          && ((Number) interval.get("window_executions")).longValue() == 0) continue;
      var cursor = new LinkedHashMap<>(row);
      cursor.putAll(interval);
      cursors.add(cursor);
    }
    if (cursors.isEmpty()) throw new IllegalStateException("Actual production claim cursor absent");
    for (var cursor : cursors) {
      var plan = jdbc.queryForList("select plan_table_output from table(dbms_xplan.display_cursor(?,?,'ALLSTATS LAST +PREDICATE +IOSTATS +HINT_REPORT +PROJECTION'))",
          String.class, cursor.get("SQL_ID"), cursor.get("CHILD_NUMBER"));
      if (plan.isEmpty() || plan.stream().noneMatch(line -> line.contains("A-Rows"))
          || plan.stream().anyMatch(line -> line.contains("cannot fetch plan")))
        throw new IllegalStateException("Actual claim execution plan unavailable");
      cursor.put("actual_plan", plan);
      cursor.put("row_sources", jdbc.queryForList("select id,operation,options,object_name,last_starts,last_output_rows,last_cr_buffer_gets,last_cu_buffer_gets,last_disk_reads,last_elapsed_time from v$sql_plan_statistics_all where sql_id=? and child_number=? order by id", cursor.get("SQL_ID"), cursor.get("CHILD_NUMBER")));
    }
    return cursors;
  }

  static List<Map<String, Object>> cursorIntervals(
      List<Map<String, Object>> before, List<Map<String, Object>> after) {
    Map<String, Map<String, Object>> previous = new LinkedHashMap<>();
    for (var row : before) previous.put(cursorSlot(row), row);
    List<Map<String, Object>> result = new ArrayList<>();
    for (var row : after) {
      var old = previous.remove(cursorSlot(row));
      String reason = old == null ? "MISSING_BEFORE" :
          !OracleCursorIdentity.from(old).equals(OracleCursorIdentity.from(row)) ? "LOAD_IDENTITY_CHANGED" : null;
      Map<String, Long> deltas = new LinkedHashMap<>();
      for (String key : List.of("EXECUTIONS", "FETCHES", "ROWS_PROCESSED", "SORTS", "BUFFER_GETS",
          "DISK_READS", "ELAPSED_TIME", "CPU_TIME", "APPLICATION_WAIT_TIME", "CONCURRENCY_WAIT_TIME", "USER_IO_WAIT_TIME")) {
        if (reason != null) break;
        if (!(old.get(key) instanceof Number first) || !(row.get(key) instanceof Number last)) continue;
        long delta = last.longValue() - first.longValue();
        if (delta < 0) reason = "COUNTER_RESET";
        else deltas.put(key, delta);
      }
      Map<String, Object> interval = new LinkedHashMap<>();
      interval.put("before", old);
      interval.put("after", row);
      interval.put("attribution", reason == null ? "VALID" : "UNATTRIBUTABLE");
      interval.put("reason", reason);
      interval.put("window_executions", reason == null ? deltas.get("EXECUTIONS") : null);
      interval.put("counter_deltas", reason == null ? deltas : null);
      result.add(interval);
    }
    for (var old : previous.values()) {
      Map<String, Object> interval = new LinkedHashMap<>();
      interval.put("before", old);
      interval.put("after", null);
      interval.put("attribution", "UNATTRIBUTABLE");
      interval.put("reason", "DISAPPEARED_CHILD");
      interval.put("window_executions", null);
      interval.put("counter_deltas", null);
      result.add(interval);
    }
    return result;
  }

  private static String cursorSlot(Map<String, Object> row) {
    return row.get("SQL_ID") + ":" + row.get("CHILD_NUMBER");
  }

  static void write(Path path, Object value) throws Exception {
    Files.createDirectories(path.getParent());
    Files.writeString(path, JsonMapper.builder().build().writeValueAsString(value) + "\n");
  }
}
