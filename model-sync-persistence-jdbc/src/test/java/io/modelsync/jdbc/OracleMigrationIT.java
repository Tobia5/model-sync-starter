package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Destructive DDL is restricted to an empty, separately acknowledged disposable test schema. */
class OracleMigrationIT {
  @Test void v1ToV2PreservesWaitingAndDeadEvidence() throws Exception {
    assertEquals("DEDICATED_MIGRATION_SCHEMA", System.getenv("MODELSYNC_ORACLE_MIGRATION_SCHEMA_ACK"));
    String url=System.getenv("MODELSYNC_ORACLE_MIGRATION_URL");
    String user=System.getenv("MODELSYNC_ORACLE_MIGRATION_USER");
    String password=System.getenv("MODELSYNC_ORACLE_MIGRATION_PASSWORD");
    assertNotNull(url); assertNotNull(user); assertNotNull(password);
    assertTrue(user.matches("MS_TEST_[A-Za-z0-9_]{1,20}"), "Migration account must be a dedicated MS_TEST_ schema");
    var ds=new DriverManagerDataSource(url,user,password);
    ds.setDriverClassName("oracle.jdbc.OracleDriver");
    var jdbc=new JdbcTemplate(ds);
    try(Connection connection=ds.getConnection()) {
      assertEquals(user.toUpperCase(),connection.getSchema().toUpperCase(),"Connected schema mismatch");
      System.out.println("Oracle migration test database version: " + connection.getMetaData().getDatabaseProductVersion());
    }
    Integer existing=jdbc.queryForObject("select count(*) from user_tables where table_name in ('MS_META','MS_WORK','MS_STATE','MS_QUOTA')",Integer.class);
    assertEquals(0,existing,"Migration fixture requires an empty isolated schema");
    List<String> created=new ArrayList<>();
    try {
      String schema=new ClassPathResource("model-sync/schema/oracle-v1.sql").getContentAsString(StandardCharsets.UTF_8);
      for(String statement:schema.split(";")) {
        String sql=statement.trim();
        if(sql.isEmpty())continue;
        jdbc.execute(sql);
        if(sql.startsWith("CREATE TABLE "))created.add(sql.split("\\s+")[2]);
      }
      jdbc.update("insert into MS_WORK (work_id,binding_id,tenant_id,subject_id,priority,generation,attempts,force_refresh,work_status,next_at_ms,requested_at_ms) values (?,?,?,?,?,1,0,0,'WAITING',0,0)","test-waiting","fixture","synthetic","1",2);
      jdbc.update("insert into MS_WORK (work_id,binding_id,tenant_id,subject_id,priority,generation,attempts,force_refresh,work_status,next_at_ms,requested_at_ms) values (?,?,?,?,?,1,1,0,'DEAD',0,0)","test-dead","fixture","synthetic","2",2);
      jdbc.update("insert into MS_STATE (work_id,outcome) values (?,?)","test-dead","MISMATCH");
      String migration=new ClassPathResource("model-sync/migration/oracle-v1-to-v2.sql").getContentAsString(StandardCharsets.UTF_8).replace("\r\n","\n");
      int start=migration.indexOf("DECLARE\n");
      int end=migration.indexOf("\n/\n",start);
      assertTrue(start>=0 && end>start,"Reviewed PL/SQL block absent");
      jdbc.execute(migration.substring(start,end).trim());
      assertEquals("2",jdbc.queryForObject("select meta_value from MS_META where meta_name='schema_version'",String.class));
      assertEquals(2,jdbc.queryForObject("select count(*) from MS_WORK",Integer.class));
      assertEquals("MISMATCH",jdbc.queryForObject("select outcome from MS_STATE where work_id='test-dead'",String.class));
      jdbc.update("update MS_WORK set work_status='IDLE' where work_id='test-waiting'");
      assertEquals("IDLE",jdbc.queryForObject("select work_status from MS_WORK where work_id='test-waiting'",String.class));
      assertTrue(new JdbcWorkStore(jdbc).supportsOnDemand());
      // The initial fixture is current; remove only its test-owned additive index to emulate v2.
      jdbc.execute("drop index MS_WORK_RECOVER_IX");
      String queueMigration = new ClassPathResource("model-sync/migration/oracle-queue-v1.sql")
          .getContentAsString(StandardCharsets.UTF_8).replace("\r\n", "\n");
      int queueStart = queueMigration.indexOf("DECLARE\n");
      int queueEnd = queueMigration.indexOf("\n/", queueStart);
      jdbc.execute(queueMigration.substring(queueStart, queueEnd));
      assertEquals(List.of("PRIORITY", "WORK_STATUS", "LEASE_UNTIL_MS", "WORK_ID"),
          jdbc.queryForList("select column_name from user_ind_columns where index_name='MS_WORK_RECOVER_IX' order by column_position", String.class));
      assertThrows(org.springframework.dao.DataAccessException.class,
          () -> jdbc.execute(queueMigration.substring(queueStart, queueEnd)), "Never silently replace an existing index");
      assertEquals(2, jdbc.queryForObject("select count(*) from MS_WORK", Integer.class));
      assertEquals("MISMATCH", jdbc.queryForObject("select outcome from MS_STATE where work_id='test-dead'", String.class));
    } finally {
      // Only the tables created after the empty-schema check may be removed.
      for(String table:List.of("MS_STATE","MS_QUOTA","MS_WORK","MS_META"))
        if(created.contains(table)) jdbc.execute("drop table " + table + " cascade constraints");
    }
  }
}
