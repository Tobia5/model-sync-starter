package io.modelsync.jdbc;

import io.modelsync.core.*;
import io.modelsync.core.port.*;
import java.net.SocketTimeoutException;
import java.sql.*;
import java.util.*;

/** Bounded facts only: never a message, entity, connection, or retained exception graph. */
record DetachedFailure(String category, String type, String sqlState, Integer vendorCode,
    boolean truncated) {
  static DetachedFailure capture(Throwable failure) {
    var pending = new ArrayDeque<Throwable>();
    var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    pending.add(failure);
    boolean contention = false, known = false, leaseLost = false, uncertain = false, truncated = false;
    String state = null;
    Integer code = null;
    int inspected = 0;
    while (!pending.isEmpty() && inspected++ < 8) {
      Throwable current = pending.removeFirst();
      if (!seen.add(current)) { truncated = true; continue; }
      String name = current.getClass().getName();
      contention |= current instanceof LocalContention;
      leaseLost |= current instanceof LeaseLost;
      known |= current instanceof SocketTimeoutException || current instanceof java.util.concurrent.TimeoutException
          || current instanceof RetryLater || current instanceof RateLimitedAfter || current instanceof Permanent;
      uncertain |= name.equals("org.springframework.transaction.TransactionSystemException")
          || name.equals("java.sql.SQLRecoverableException");
      if (current instanceof SQLException sql) {
        if (code == null) {
          code = sql.getErrorCode();
          state = sql.getSQLState();
          if (state != null && !state.matches("[A-Za-z0-9]{5}")) { state = null; truncated = true; }
        }
        uncertain |= sql.getSQLState() != null && sql.getSQLState().startsWith("08")
            || Set.of(17002, 17008, 17410).contains(sql.getErrorCode());
        known |= sql instanceof SQLTimeoutException || sql instanceof SQLTransactionRollbackException
            || Set.of(1, 54, 60, 1013, 30006).contains(sql.getErrorCode());
        if (sql.getNextException() != null) pending.addLast(sql.getNextException());
      }
      if (current.getCause() != null) pending.addLast(current.getCause());
    }
    truncated |= !pending.isEmpty();
    String type = failure.getClass().getName();
    if (type.length() > 160 || !type.matches("[A-Za-z0-9_.$]+")) { type = "UNQUALIFIED_TYPE"; truncated = true; }
    if (contention && code != null && code != 54) truncated = true;
    String category = uncertain ? "UNCERTAIN_COMMIT" : truncated ? "UNKNOWN"
        : contention ? "LOCAL_CONTENTION" : leaseLost ? "LEASE_LOST" : known ? "KNOWN" : "UNKNOWN";
    return new DetachedFailure(category, type, state, code, truncated);
  }

  Map<String, Object> facts() {
    var values = new LinkedHashMap<String, Object>();
    values.put("category", category); values.put("exception_type", type);
    values.put("sql_state", sqlState); values.put("vendor_code", vendorCode);
    values.put("truncated", truncated);
    return values;
  }
}
