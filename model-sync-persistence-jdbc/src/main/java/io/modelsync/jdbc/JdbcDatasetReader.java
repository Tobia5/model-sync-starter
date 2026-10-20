package io.modelsync.jdbc;

import io.modelsync.core.DatasetPort;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Read a source-defined manifest view with bound parameters; no external SQL comes from end users.
 */
public final class JdbcDatasetReader implements DatasetPort.Reader {
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
  public DatasetPort.Manifest inspect(DatasetPort.Slice requested) {
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
          DatasetPort.Slice actual =
              new DatasetPort.Slice(
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
          return new DatasetPort.Manifest(
              actual, rows, unresolved, totals, rs.getString("content_digest"));
        });
  }
}
