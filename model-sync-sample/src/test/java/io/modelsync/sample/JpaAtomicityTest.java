package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import io.modelsync.core.*;
import jakarta.persistence.*;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

@SpringBootTest(
    classes = SampleApplication.class,
    properties = {"sample.run-demo=false", "model-sync.quotas.customer-owner=1ms"})
class JpaAtomicityTest {
  @Autowired ModelSync<Customer> sync;
  @Autowired SyncCoordinator coordinator;
  @Autowired CustomerService service;
  @Autowired LocalTransactions tx;
  @Autowired EntityManagerFactory emf;
  @Autowired DataSource dataSource;

  @Test
  void reviewMutationAndIntentRollbackTogether() throws Exception {
    String id = UUID.randomUUID().toString();
    sync.request("test", id, Priority.URGENT, true);
    for (int i = 0; i < 50 && !sync.status("test", id).matched(); i++) {
      coordinator.runOne();
      Thread.sleep(10);
    }
    assertTrue(sync.status("test", id).matched());
    var jdbc = new JdbcTemplate(dataSource);
    String workId = new WorkKey("customer", new SubjectKey("test", id)).storageId();
    Long generation =
        jdbc.queryForObject("select generation from MS_WORK where work_id=?", Long.class, workId);
    EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(emf);
    assertThrows(
        IllegalStateException.class,
        () ->
            tx.required(
                () -> {
                  service.requestReview("test", id);
                  throw new IllegalStateException("business rollback");
                }));
    tx.required(
        () -> {
          var entity =
              em.find(
                  CustomerEntity.class, CustomerJpaMapping.storageId(new SubjectKey("test", id)));
          assertNull(entity.reviewRequestedAt);
          return null;
        });
    assertEquals(
        generation,
        jdbc.queryForObject("select generation from MS_WORK where work_id=?", Long.class, workId));
  }
}
