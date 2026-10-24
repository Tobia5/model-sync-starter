package io.modelsync.jdbc;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit capability for single-row adapter-owned work/quota creation, not JPA or all DML. */
public enum JdbcInsertPolicy {
  DEFAULT,
  ORACLE_26_NOWAIT;

  String suffix() { return this == ORACLE_26_NOWAIT ? " nowait" : ""; }

  void validate(JdbcTemplate jdbc) {
    if (this == DEFAULT) return;
    Boolean supported = jdbc.execute((ConnectionCallback<Boolean>) connection -> {
      var metadata = connection.getMetaData();
      String version = metadata.getDatabaseProductVersion();
      // Oracle26ai's JDBC database version is23.26, not major26. Fail closed on unqualified lines.
      return "Oracle".equals(metadata.getDatabaseProductName())
          && metadata.getDatabaseMajorVersion() == 23 && metadata.getDatabaseMinorVersion() == 26
          && version != null && version.contains("26ai");
    });
    if (!Boolean.TRUE.equals(supported))
      throw new IllegalArgumentException("ORACLE_26_NOWAIT inserts require qualified Oracle26ai (JDBC23.26) metadata; default support is unchanged");
  }
}
