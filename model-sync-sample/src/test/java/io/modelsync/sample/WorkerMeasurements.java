package io.modelsync.sample;

import io.modelsync.reactor.ReactorSyncWorker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

final class WorkerMeasurements {
  private WorkerMeasurements() {}
  static Map<String, Long> counters(WorkerJdbcTemplate jdbc) {
    Map<String, Long> counters = new LinkedHashMap<>();
    jdbc.query("select n.name,sum(s.value) from v$sesstat s join v$statname n on n.statistic#=s.statistic#"
        + " join v$session x on x.sid=s.sid where x.username=user and n.name in"
        + " ('db block gets','consistent gets','physical reads','execute count','parse count (total)',"
        + "'redo size','user commits','sorts (rows)') group by n.name", row -> {
          counters.put(row.getString(1), row.getLong(2));
        });
    return counters;
  }
  static Map<String, Long> scheduling(ReactorSyncWorker worker, WorkerJdbcTemplate jdbc,
      WorkerOwner owner, WorkerQuota quota) {
    var a = worker.activity();
    Map<String, Long> values = new LinkedHashMap<>();
    values.put("ticks", a.ticks()); values.put("empty", a.empty()); values.put("deferred", a.deferred());
    values.put("failed", a.failed()); values.put("fetched", a.fetched()); values.put("cancelled", a.cancelled());
    values.put("selector_callbacks", jdbc.selectors.get());
    values.put("owner_calls", (long) owner.calls.get());
    values.put("quota_grants", quota.grants.get()); values.put("quota_denials", quota.denials.get());
    values.put("completed_syncs", jdbc.queryForObject("select count(*) from MS_STATE s join MS_WORK w"
        + " on w.work_id=s.work_id where w.binding_id in ('worker-main','worker-other')"
        + " and s.outcome='APPLIED'", Long.class));
    return values;
  }
  static Map<String, Long> delta(Map<String, Long> before, Map<String, Long> after) {
    Map<String, Long> delta = new LinkedHashMap<>();
    before.forEach((key, value) -> {
      if (!after.containsKey(key) || after.get(key) < value)
        throw new IllegalStateException("Worker session/counter continuity lost: " + key);
      delta.put(key, after.get(key) - value);
    });
    return delta;
  }
  static void write(Map<String, Object> report) throws Exception {
    Path path = directory().resolve("oracle-worker.json");
    Files.createDirectories(path.getParent());
    Files.writeString(path, JsonMapper.builder().build().writeValueAsString(report) + "\n");
  }
  static Path directory() {
    return Path.of(System.getProperty("modelsync.repository.root",
        Path.of("..").toAbsolutePath().normalize().toString())).resolve("model-sync-sample/target/qm");
  }
}
