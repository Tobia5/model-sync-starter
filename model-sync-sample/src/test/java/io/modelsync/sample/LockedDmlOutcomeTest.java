package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import org.junit.jupiter.api.Test;

class LockedDmlOutcomeTest {
  @Test void recognizesPinnedDriverInterruptedReadOnlyWithSocketTimeoutCause() {
    assertTrue(LockedDmlOutcome.readTimeout(new SQLRecoverableException("synthetic", "08006", 18730,
        new SocketTimeoutException("synthetic"))));
    assertFalse(LockedDmlOutcome.readTimeout(new SQLRecoverableException("synthetic", "08006", 18730)));
  }
  @Test void recognizesOlderIoTimeoutOnlyWithSocketTimeoutCause() {
    assertTrue(LockedDmlOutcome.readTimeout(new SQLRecoverableException("synthetic", "08006", 17002,
        new SocketTimeoutException("synthetic"))));
  }
  @Test void rejectsArbitraryIoAndDatabaseErrors() {
    assertFalse(LockedDmlOutcome.readTimeout(new SQLRecoverableException("synthetic", "08006", 18731,
        new SocketTimeoutException("synthetic"))));
    assertFalse(LockedDmlOutcome.readTimeout(new SQLException("synthetic", "08006", 18730,
        new SocketTimeoutException("synthetic"))));
  }
  @Test void keepsCancellationAndNetworkTimeoutDistinct() {
    var timeout = new SQLRecoverableException("synthetic", "08006", 18730, new SocketTimeoutException());
    assertFalse(LockedDmlOutcome.hasSqlCode(timeout, 1013));
    assertFalse(LockedDmlOutcome.readTimeout(new SQLException("synthetic", "72000", 1013)));
  }
}
