package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.modelsync.jdbc.*;
import io.modelsync.jpa.*;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** Cheap negative guard: a bounded-policy fixture must never silently use DEFAULT adapters. */
class QualificationTargetTest {
  @Test void defaultAdaptersCannotSatisfyNamedTarget() {
    var source = new DriverManagerDataSource("jdbc:h2:mem:target-guard", "sa", "");
    var jdbc = new JdbcTemplate(source);
    var projection = new JpaProjectionStore<>(mock(EntityManager.class), new CustomerJpaMapping());
    assertThrows(AssertionError.class, () -> QualificationTargetAssertions.policies(new JdbcWorkStore(jdbc),
        new JdbcQuotaGate(jdbc, new DataSourceTransactionManager(source), Clock.systemUTC()), projection));
  }
}
