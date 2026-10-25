package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;
import io.modelsync.core.port.LocalContention;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.*;

class CooldownRetryTest {
  @Test void fourRolledBackTransactionsWithJitterOutsideTheirLocks() {
    var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
    var jdbc=new JdbcTemplate(source);jdbc.execute("create table guard(guard_value integer)");jdbc.update("insert into guard values(0)");
    var tx=new TransactionTemplate(new DataSourceTransactionManager(source));var calls=new AtomicInteger();var sleeps=new ArrayList<Long>();
    var retry=new CooldownRetry(()->1,millis->{
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      assertEquals(0,jdbc.queryForObject("select guard_value from guard",Integer.class));sleeps.add(millis);
    });
    assertThrows(LocalContention.class,()->retry.run(()->tx.execute(s->{
      calls.incrementAndGet();jdbc.update("update guard set guard_value=1");throw new LocalContention("fixture",null);
    })));
    assertEquals(4,calls.get());assertEquals(List.of(10L,20L,40L),sleeps);
    assertEquals(0,jdbc.queryForObject("select guard_value from guard",Integer.class));
  }
  @Test void unexpectedOrUncertainErrorsAreNeverRetried() {
    for(RuntimeException failure:List.of(new IllegalStateException("fixture"),new TransactionSystemException("uncertain"))) {
      var calls=new AtomicInteger();var retry=new CooldownRetry(()->0,millis->fail("Unexpected retry"));
      assertSame(failure,assertThrows(RuntimeException.class,()->retry.run(()->{calls.incrementAndGet();throw failure;})));
      assertEquals(1,calls.get());
    }
  }
  @Test void retryInterruptionStopsWithoutAnotherTransaction() {
    var calls=new AtomicInteger();
    var retry=new CooldownRetry(()->0,millis->{Thread.currentThread().interrupt();CooldownRetry.sleep(millis);});
    try {assertThrows(java.util.concurrent.CancellationException.class,()->retry.run(()->{calls.incrementAndGet();throw new LocalContention("fixture",null);}));
      assertEquals(1,calls.get());assertTrue(Thread.currentThread().isInterrupted());}
    finally {Thread.interrupted();}
  }
}
