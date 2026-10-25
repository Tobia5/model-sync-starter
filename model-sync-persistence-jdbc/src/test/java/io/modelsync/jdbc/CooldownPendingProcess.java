package io.modelsync.jdbc;

import com.zaxxer.hikari.*;
import io.modelsync.core.port.CooldownRequest;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** Owned independent JVM; checkpoint follows durable acceptance, not a provisional callback. */
public final class CooldownPendingProcess {
  private CooldownPendingProcess(){}
  public static void main(String[] args)throws Exception {
    try(var source=OracleCooldownIT.source("cooldown-child",1)) {
      var jdbc=new JdbcTemplate(source);var manager=new DataSourceTransactionManager(source);
      var store=new JdbcCooldownPublications(jdbc,manager,Clock.systemUTC(),JdbcQuotaLockPolicy.ORACLE_NOWAIT,JdbcInsertPolicy.ORACLE_26_NOWAIT,new CooldownRetry(()->.5,CooldownRetry::sleep));
      var receipt=store.accept(new CooldownRequest(args[0],args[1],Instant.ofEpochMilli(Long.parseLong(args[2]))));
      var id=jdbc.queryForMap("select sid,serial# from v$session where sid=to_number(sys_context('USERENV','SID'))");
      Files.writeString(Path.of(args[3]),tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(java.util.Map.of("state",receipt.state().name(),"session",id)));
      if(new CountDownLatch(1).await(30,TimeUnit.SECONDS))throw new IllegalStateException("Unexpected signal");
      throw new IllegalStateException("Parent did not terminate owned process");
    }
  }
}
