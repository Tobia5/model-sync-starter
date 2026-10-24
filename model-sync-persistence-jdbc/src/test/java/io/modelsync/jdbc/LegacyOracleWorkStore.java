package io.modelsync.jdbc;

import static org.junit.jupiter.api.Assertions.*;
import io.modelsync.core.*;
import io.modelsync.core.domain.*;
import io.modelsync.core.port.WorkStore;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Frozen 1a9187c OR selector for comparisons only; never shipped or substituted for production. */
final class LegacyOracleWorkStore extends ForwardingWorkStore {
  private final JdbcTemplate jdbc;
  LegacyOracleWorkStore(JdbcTemplate jdbc, WorkStore delegate) { super(delegate); this.jdbc=jdbc; }
  @Override public Optional<WorkLease> claim(Priority p, Instant now, Duration duration) {
    return claim(p, now, duration, null);
  }
  @Override public Optional<WorkLease> claim(Priority p, Instant now, Duration duration, Set<String> bindings) {
    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
    List<Object> args = new ArrayList<>(); args.add(p.ordinal());
    String waiting = "work_status='WAITING' and next_at_ms<=?";
    if (bindings != null && bindings.isEmpty()) waiting = "1=0";
    else {
      args.add(now.toEpochMilli());
      if (bindings != null) {
        waiting += " and binding_id in (" + String.join(",", Collections.nCopies(bindings.size(), "?")) + ")";
        args.addAll(bindings.stream().sorted().toList());
      }
    }
    args.add(now.toEpochMilli());
    String sql = "select * from MS_WORK where priority=? and ((" + waiting
        + ") or (work_status='LEASED' and lease_until_ms<=?)) order by next_at_ms,work_id for update skip locked";
    var selected = jdbc.execute((ConnectionCallback<Optional<WorkLease>>) connection -> {
      try (var statement = connection.prepareStatement(sql)) {
        for (int i=0;i<args.size();i++) statement.setObject(i+1,args.get(i));
        statement.setFetchSize(1); statement.setMaxRows(1);
        try (var row = statement.executeQuery()) {
          if (!row.next()) return Optional.empty();
          var key = new WorkKey(row.getString("binding_id"),new SubjectKey(row.getString("tenant_id"),row.getString("subject_id")));
          return Optional.of(new WorkLease(key,UUID.randomUUID().toString(),row.getLong("generation"),
              row.getInt("attempts"),row.getInt("force_refresh")==1,p,now.plus(duration)));
        }
      }
    });
    if (selected.isPresent()) {
      var lease=selected.orElseThrow();
      assertEquals(1,jdbc.update("update MS_WORK set work_status='LEASED',lease_token=?,lease_until_ms=?,leased_generation=generation where work_id=?",
          lease.token(),lease.expiresAt().toEpochMilli(),lease.key().storageId()));
    }
    return selected;
  }
}
