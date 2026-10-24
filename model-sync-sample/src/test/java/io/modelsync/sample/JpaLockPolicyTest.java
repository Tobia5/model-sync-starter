package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.modelsync.core.domain.*;
import io.modelsync.core.port.LocalContention;
import io.modelsync.jpa.*;
import jakarta.persistence.*;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class JpaLockPolicyTest {
  private final EntityManager em = mock(EntityManager.class);
  @SuppressWarnings("unchecked")
  private final LockingJpaProjectionMapping<String, Object> mapping = mock(LockingJpaProjectionMapping.class);
  private final SubjectKey key = new SubjectKey("synthetic", "known");
  private final Object entity = new Object();
  @BeforeEach void open() {
    TransactionSynchronizationManager.setActualTransactionActive(true);
    when(em.isJoinedToTransaction()).thenReturn(true);
    when(mapping.snapshot(entity)).thenReturn(new Projection<>("before", "0", null));
  }
  @AfterEach void close() { TransactionSynchronizationManager.clear(); }

  @Test void enabledPolicyIsPassedToLookupAndRefreshPerOperation() {
    when(mapping.find(em, key, LockModeType.PESSIMISTIC_WRITE, JpaLockPolicy.ORACLE_NOWAIT)).thenReturn(Optional.of(entity));
    var store = new JpaProjectionStore<>(em, mapping, JpaLockPolicy.ORACLE_NOWAIT);
    assertTrue(store.compareAndSet(key, "0", "after", null));
    verify(em).refresh(entity, LockModeType.PESSIMISTIC_WRITE, JpaLockPolicy.ORACLE_NOWAIT.properties());
    verify(em, never()).setProperty(anyString(), any());
    verify(em).flush();
  }
  @Test void defaultKeepsLegacyMappingAndRefreshCalls() {
    when(mapping.find(em, key, LockModeType.PESSIMISTIC_WRITE)).thenReturn(Optional.of(entity));
    assertTrue(new JpaProjectionStore<>(em, mapping).compareAndSet(key, "0", "after", null));
    verify(em).refresh(entity, LockModeType.PESSIMISTIC_WRITE);
    verify(mapping, never()).find(any(), any(), any(), any());
  }
  @Test void legacyMappingCannotSilentlyOptIn() {
    @SuppressWarnings("unchecked") JpaProjectionMapping<String, Object> legacy = mock(JpaProjectionMapping.class);
    assertDoesNotThrow(() -> new JpaProjectionStore<>(em, legacy));
    assertThrows(IllegalArgumentException.class, () -> new JpaProjectionStore<>(em, legacy, JpaLockPolicy.ORACLE_NOWAIT));
    verifyNoInteractions(legacy);
  }
  @Test void databaseRefusalAtRefreshStopsMutationAndFlush() {
    when(mapping.find(em, key, LockModeType.PESSIMISTIC_WRITE, JpaLockPolicy.ORACLE_NOWAIT)).thenReturn(Optional.of(entity));
    var refused = new LockTimeoutException("synthetic", new SQLException("synthetic", "61000", 54), entity);
    doThrow(refused).when(em).refresh(entity, LockModeType.PESSIMISTIC_WRITE, JpaLockPolicy.ORACLE_NOWAIT.properties());
    var failure = assertThrows(LocalContention.class, () ->
        new JpaProjectionStore<>(em, mapping, JpaLockPolicy.ORACLE_NOWAIT).compareAndSet(key, "0", "after", null));
    var diagnostic = assertInstanceOf(SQLException.class, failure.getCause());
    assertEquals(54, diagnostic.getErrorCode()); assertEquals("61000", diagnostic.getSQLState());
    assertNull(diagnostic.getCause()); assertNull(diagnostic.getNextException());
    assertNotSame(refused, diagnostic);
    verify(mapping, never()).update(any(), any(), any()); verify(em, never()).flush();
  }
  @Test void transportAndOtherFailuresDoNotBecomeLockRefusal() {
    for (int code : new int[] {1, 1013, 18730, 17002}) {
      var failure = new PersistenceException("synthetic", new SQLException("synthetic", "72000", code));
      doThrow(failure).when(mapping).find(em, key, LockModeType.PESSIMISTIC_WRITE, JpaLockPolicy.ORACLE_NOWAIT);
      assertSame(failure, assertThrows(PersistenceException.class, () ->
          new JpaProjectionStore<>(em, mapping, JpaLockPolicy.ORACLE_NOWAIT).compareAndSet(key, "0", "after", null)));
    }
  }
  @Test void defaultPolicyPreservesExistingExceptionBehavior() {
    var failure = new PersistenceException("synthetic", new SQLException("synthetic", "61000", 54));
    when(mapping.find(em, key, LockModeType.PESSIMISTIC_WRITE)).thenThrow(failure);
    assertSame(failure, assertThrows(PersistenceException.class, () ->
        new JpaProjectionStore<>(em, mapping).compareAndSet(key, "0", "after", null)));
  }
}
