package io.modelsync.jdbc;
import io.modelsync.core.port.*;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.port.DatasetReader;
import io.modelsync.core.DatasetSlice;
import io.modelsync.core.DatasetManifest;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Read a source-defined manifest view with bound parameters; no external SQL comes from end users.
 */
public final class JdbcDatasetReader implements DatasetReader {
  private final NamedParameterJdbcTemplate jdbc;
  private final String query;
  private final Map<String, String> totalColumns;

  public JdbcDatasetReader(
      NamedParameterJdbcTemplate jdbc, String reviewedQuery, Map<String, String> totalColumns) {
    this.jdbc = jdbc;
    this.query = reviewedQuery;
    this.totalColumns = Map.copyOf(totalColumns);
  }

  @Override
  public DatasetManifest inspect(DatasetSlice requested) {
    Map<String, Object> p = new HashMap<>();
    p.put("dataset", requested.dataset());
    p.put("tenant", requested.tenant());
    p.put("snapshot", requested.snapshot());
    p.put("partition", requested.partition());
    p.put("mappingVersion", requested.mappingVersion());
    p.put("grain", requested.grain());
    return jdbc.queryForObject(
        query,
        p,
        (rs, row) -> {
          DatasetSlice actual =
              new DatasetSlice(
                  rs.getString("dataset_id"),
                  rs.getString("tenant_id"),
                  rs.getString("snapshot_id"),
                  rs.getString("partition_id"),
                  rs.getString("mapping_version"),
                  rs.getString("grain"));
          Map<String, BigDecimal> totals = new LinkedHashMap<>();
          for (var total : totalColumns.entrySet())
            totals.put(
                total.getKey(),
                Objects.requireNonNull(rs.getBigDecimal(total.getValue()), "Null business total"));
          long rows = rs.getLong("row_count");
          if (rs.wasNull()) throw new java.sql.SQLException("Manifest row_count is NULL");
          long unresolved = rs.getLong("unresolved_count");
          if (rs.wasNull()) throw new java.sql.SQLException("Manifest unresolved_count is NULL");
          return new DatasetManifest(
              actual, rows, unresolved, totals, rs.getString("content_digest"));
        });
  }
}
