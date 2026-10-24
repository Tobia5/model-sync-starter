package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;
import io.modelsync.core.Priority;
import io.modelsync.core.domain.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

class SelectorJdbcTemplateTest {
  @Test void countsSelectorQueriesRatherThanMetadataStateReadsOrLedgerAttempts() {
    var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
    new ResourceDatabasePopulator(new ClassPathResource("model-sync/schema/h2-v2.sql")).execute(source);
    var jdbc=new SelectorJdbcTemplate(source);var store=new JdbcWorkStore(jdbc);
    var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
    var now=Instant.now();store.verifySchema();assertEquals(0,jdbc.selectorQueries.get());
    assertTrue(tx.execute(s->store.claim(Priority.NORMAL,now,Duration.ofSeconds(60))).isEmpty());
    assertEquals(2,jdbc.selectorQueries.get());
    var key=new WorkKey("telemetry",new SubjectKey("synthetic","1"));
    tx.executeWithoutResult(s->store.enqueue(key,Priority.NORMAL,true,now));
    var lease=tx.execute(s->store.claim(Priority.NORMAL,now,Duration.ofSeconds(60))).orElseThrow();
    assertEquals(4,jdbc.selectorQueries.get());
    tx.executeWithoutResult(s->{assertTrue(store.lockValid(lease,now));store.state(key);});
    assertEquals(4,jdbc.selectorQueries.get());
  }
}
