package io.modelsync.sample;

import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;

/** Narrow diagnostic recognition; a read timeout is never statement-cancellation evidence. */
final class LockedDmlOutcome {
  private LockedDmlOutcome() {}

  static boolean hasSqlCode(Throwable error, int code) {
    for (Throwable current = error; current != null; current = current.getCause())
      if (current instanceof SQLException sql && sql.getErrorCode() == code) return true;
    return false;
  }

  static boolean readTimeout(Throwable error) {
    boolean supportedCode = false;
    boolean socketTimeout = false;
    for (Throwable current = error; current != null; current = current.getCause()) {
      if (current instanceof SQLRecoverableException sql
          && (sql.getErrorCode() == 17002 || sql.getErrorCode() == 18730)) supportedCode = true;
      if (current instanceof SocketTimeoutException) socketTimeout = true;
    }
    return supportedCode && socketTimeout;
  }
}
