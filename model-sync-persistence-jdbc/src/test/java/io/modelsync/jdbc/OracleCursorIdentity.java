package io.modelsync.jdbc;

import java.util.Map;
import java.util.Objects;

/** SQL_ID/CHILD_NUMBER can be reused after invalidation; identify the loaded cursor as well. */
record OracleCursorIdentity(String sqlId, long child, String address, String loadedAt, long loads) {
  static OracleCursorIdentity from(Map<String, Object> row) {
    return new OracleCursorIdentity(Objects.requireNonNull(row.get("SQL_ID")).toString(),
        ((Number) row.get("CHILD_NUMBER")).longValue(),
        Objects.requireNonNull(row.get("CHILD_ADDRESS")).toString(),
        Objects.requireNonNull(row.get("LAST_LOAD_TIME")).toString(),
        ((Number) row.get("LOADS")).longValue());
  }
}
