package io.modelsync.jdbc;

import io.modelsync.core.port.*;
import java.sql.SQLException;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

/** Immutable receipts protect admission before independently retrying quota materialization. */
final class JdbcCooldownPublications {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate accept, apply;
  private final Clock clock;
  private final JdbcQuotaLockPolicy locks;
  private final JdbcInsertPolicy inserts;
  private final CooldownRetry retry;

  JdbcCooldownPublications(JdbcTemplate jdbc, PlatformTransactionManager manager, Clock clock,
      JdbcQuotaLockPolicy locks, JdbcInsertPolicy inserts, CooldownRetry retry) {
    this.jdbc=jdbc;this.clock=clock;this.locks=locks;this.inserts=inserts;this.retry=retry;
    accept=transaction(manager,"modelSyncCooldownAccept");apply=transaction(manager,"modelSyncCooldownApply");
    if (!"1".equals(jdbc.queryForObject("select meta_value from MS_META where meta_name='cooldown_schema_version'",String.class)))
      throw new IllegalStateException("DURABLE cooldowns require explicit cooldown schema v1");
    jdbc.query("select operation_id,quota_group,deadline_ms,publication_status from MS_COOLDOWN where 1=0",r->{});
  }
  private static TransactionTemplate transaction(PlatformTransactionManager manager,String name) {
    var tx=new TransactionTemplate(manager);tx.setName(name);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);return tx;
  }
  CooldownReceipt accept(CooldownRequest request) {
    requireOutsideTransaction();
    return retry.run(()->transaction(accept,"cooldown acceptance",()->{
      var old=read(request.operationId(),false);
      if(old.isPresent())return canonical(request,old.orElseThrow());
      if(!Integer.valueOf(1).equals(jdbc.queryForObject("select count(*) from MS_QUOTA where quota_group=?",Integer.class,request.group())))
        throw new IllegalStateException("Cooldown requires a registered quota group");
      try {jdbc.update("insert into MS_COOLDOWN(operation_id,quota_group,deadline_ms,accepted_at_ms,publication_status) values(?,?,?,?,'PENDING')"+inserts.suffix(),
          request.operationId(),request.group(),request.until().toEpochMilli(),clock.millis());}
      catch(DuplicateKeyException duplicate) {return canonical(request,read(request.operationId(),false).orElseThrow());}
      return new CooldownReceipt(request,CooldownState.DURABLE_PENDING,true);
    }));
  }
  CooldownReceipt materialize(CooldownReceipt accepted) {
    if(accepted.state()==CooldownState.APPLIED)return accepted;
    try {return retry.run(()->transaction(apply,"cooldown application",()->{
      String group=accepted.request().group();
      Long previous=jdbc.queryForObject("select next_allowed_ms from MS_QUOTA where quota_group=? for update"+locks.suffix(),Long.class,group);
      if(previous==null)throw new IllegalStateException("Unknown quota");
      var own=canonical(accepted.request(),read(accepted.request().operationId(),true).orElseThrow());
      long until=Math.max(previous,Math.max(floor(group),own.request().until().toEpochMilli()));
      if(jdbc.update("update MS_QUOTA set next_allowed_ms=? where quota_group=?",until,group)!=1)
        throw new IllegalStateException("Quota application did not update exactly one row");
      markApplied(own.request().operationId());
      return new CooldownReceipt(own.request(),CooldownState.APPLIED,true);
    }));}
    catch(LocalContention | CooldownRetryInterrupted boundedRefusal) {
      // Acceptance already committed. No false completion: every admission still reads this floor.
      return accepted;
    }
  }
  long floor(String group) {
    Long floor=jdbc.queryForObject("select max(deadline_ms) from MS_COOLDOWN where quota_group=? and publication_status='PENDING'",Long.class,group);
    return floor==null?Long.MIN_VALUE:floor;
  }
  Optional<CooldownRequest> oldest(String group) {
    return jdbc.query("select operation_id,deadline_ms from MS_COOLDOWN where quota_group=? and publication_status='PENDING' order by accepted_at_ms,operation_id fetch first 1 rows only",
        (r,n)->new CooldownRequest(r.getString(1),group,java.time.Instant.ofEpochMilli(r.getLong(2))),group).stream().findFirst();
  }
  /** Caller holds quota first. Only compliant materializers change receipts, under the same fence. */
  void recoverOne(String group,long storedFloor) {
    var first=oldest(group);if(first.isEmpty())return;
    var row=read(first.orElseThrow().operationId(),true).orElseThrow();
    if(row.request().until().toEpochMilli()>storedFloor) return; // newly accepted after this admission's guard
    markApplied(row.request().operationId());
  }
  private void markApplied(String id) {
    if(jdbc.update("update MS_COOLDOWN set publication_status='APPLIED' where operation_id=?",id)!=1)
      throw new IllegalStateException("Cooldown application did not update exactly one receipt");
  }
  private Optional<CooldownReceipt> read(String id,boolean lock) {
    return jdbc.query("select quota_group,deadline_ms,publication_status from MS_COOLDOWN where operation_id=?"+(lock?" for update"+locks.suffix():""),
        (r,n)->new CooldownReceipt(new CooldownRequest(id,r.getString(1),java.time.Instant.ofEpochMilli(r.getLong(2))),
            r.getString(3).equals("APPLIED")?CooldownState.APPLIED:CooldownState.DURABLE_PENDING,true),id).stream().findFirst();
  }
  private static CooldownReceipt canonical(CooldownRequest request,CooldownReceipt stored) {
    if(!request.group().equals(stored.request().group()))throw new IllegalArgumentException("Cooldown identity reused for another group");
    return stored; // First accepted outcome wins, including duplicate delivery after an uncertain ACK.
  }
  private <T> T transaction(TransactionTemplate tx,String name,Supplier<T> action) {
    var original=new DataAccessException[1];boolean[] returned={false};
    try{return tx.execute(s->{
      try {T value=action.get();returned[0]=true;return value;}
      catch(DataAccessException failure) {original[0]=failure;throw failure;}
    });}
    catch(DataAccessException failure) {
      if(returned[0] || original[0]!=failure)
        throw new TransactionSystemException("Cooldown transaction completion is unconfirmed",failure);
      SQLException refused=JdbcWorkStore.oracleLockRefusal(failure);
      if(refused!=null && (locks==JdbcQuotaLockPolicy.ORACLE_NOWAIT || inserts==JdbcInsertPolicy.ORACLE_26_NOWAIT))
        throw new LocalContention(name,new SQLException("Oracle lock refusal",refused.getSQLState(),refused.getErrorCode()));
      throw failure;
    } catch(LocalContention unconfirmed) {
      throw new TransactionSystemException("Unexpected cooldown completion refusal",unconfirmed);
    } catch(RuntimeException unexpected) {
      if(returned[0])throw new TransactionSystemException("Cooldown transaction completion is unconfirmed",unexpected);
      throw unexpected;
    }
  }
  static void requireOutsideTransaction() {
    if(TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Cooldown publication/retry must not hold a caller transaction");
  }
}
