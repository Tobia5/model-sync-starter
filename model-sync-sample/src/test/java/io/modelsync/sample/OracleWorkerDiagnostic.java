package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;

/** Four independent small cells; neither a full suite nor a relaxation of the retained deadline. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class OracleWorkerDiagnostic {
  private final List<Map<String,Object>> cells = new ArrayList<>();
  private Map<String,Object> report;
  @BeforeAll void declare() throws Exception {
    report=FaultEvidence.report("worker-deadline-four-cell-diagnostic"); report.put("cells",cells);
    report.put("condition_ms",20000);report.put("measured_completions",160);report.put("warmup_completions",10);
    report.put("workers",4);report.put("pool",8);report.put("persistence_threads",4);report.put("owner_threads",8);
    report.put("owner_spacing_ms",20);report.put("owner_latency_ms",5);
    report.put("history_model","recorded table cardinality/block estimate only; physical segment and index history NOT_REPRODUCED");
    for(String name:List.of("fresh-fixed","fresh-adaptive","statistics-fixed","statistics-adaptive")) {
      var cell=new LinkedHashMap<String,Object>();cell.put("name",name);cell.put("status","NOT_RUN");cells.add(cell);
    }
    FaultEvidence.write("oracle-worker-diagnostic.json",report);
  }
  @Test void aFreshFixed() throws Exception { run(0,false,false); }
  @Test void bFreshAdaptive() throws Exception { run(1,true,false); }
  @Test void cStatisticsFixed() throws Exception { run(2,false,true); }
  @Test void dStatisticsAdaptive() throws Exception { run(3,true,true); }

  private void run(int index,boolean adaptive,boolean history) throws Exception {
    var cell=cells.get(index);cell.put("status","FAILED");long began=System.nanoTime();
    var fixture=new OracleWorkerIT(); WorkerTrace trace=null;
    com.zaxxer.hikari.HikariDataSource source=null;io.modelsync.reactor.ReactorSyncWorker worker=null;
    try {
      fixture.open(adaptive);source=fixture.source();worker=fixture.worker();trace=fixture.trace();
      cell.put("fresh_snapshot",fixture.diagnosticSnapshot());
      if(history)fixture.recordedStatistics();
      cell.put("before",fixture.diagnosticSnapshot());trace.begin();
      fixture.productive((String)cell.get("name"));
      cell.put("status","PASS");
    } catch(Exception | AssertionError failure) {
      cell.put("failure_type",failure.getClass().getName());throw failure;
    } finally {
      if(trace!=null)cell.put("trace",trace.end());
      cell.put("phases",fixture.diagnosticPhases());
      try { if(source!=null)cell.put("after",fixture.diagnosticSnapshot()); }
      finally {
        try { fixture.close(); }
        finally {
          cell.put("elapsed_ms",(System.nanoTime()-began)/1000000);
          if(source!=null)cell.put("pool_closed",source.isClosed());
          if(worker!=null){cell.put("shutdown",worker.shutdown());cell.put("physical_after_close",worker.physicalOperations());}
          report.put("status",cells.stream().allMatch(c->c.get("status").equals("PASS"))?"PASS":"FAILED");
          FaultEvidence.write("oracle-worker-diagnostic.json",report);
        }
      }
    }
  }
}
