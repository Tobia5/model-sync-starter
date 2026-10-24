-- jdbc-point
select * from MS_WORK where work_id=? for update nowait;

-- jpa-refresh
select * from MS_WORK where work_id=? for update nowait;

select ce1_0.storage_key,ce1_0.display_name,ce1_0.expires_at,ce1_0.review_requested_at,ce1_0.source_id,ce1_0.customer_status,ce1_0.tenant_id,ce1_0.local_version from sample_customer ce1_0 where ce1_0.storage_key=?;

select ce1_0.storage_key,ce1_0.display_name,ce1_0.expires_at,ce1_0.review_requested_at,ce1_0.source_id,ce1_0.customer_status,ce1_0.tenant_id,ce1_0.local_version from sample_customer ce1_0 where ce1_0.storage_key=? for update of ce1_0.storage_key nowait;

-- queue-skip-locked-separate
select /*+ index_asc(w MS_WORK_BIND_READY_IX) first_rows(1) */ w.* from MS_WORK w where binding_id=:1 and priority=:2 and work_status='WAITING' and next_at_ms<=:3 order by next_at_ms for update skip locked;

select /*+ index_asc(w MS_WORK_RECOVER_IX) first_rows(1) */ w.* from MS_WORK w where priority=:1 and work_status='LEASED' and lease_until_ms<=:2 order by lease_until_ms for update skip locked;

select /*+ index_asc(w MS_WORK_READY_IX) first_rows(1) */ w.* from MS_WORK w where priority=:1 and work_status='WAITING' and next_at_ms<=:2 order by next_at_ms for update skip locked;

-- success-generation-fencing-revision
select * from MS_WORK where work_id=? for update nowait;

select ce1_0.storage_key,ce1_0.display_name,ce1_0.expires_at,ce1_0.review_requested_at,ce1_0.source_id,ce1_0.customer_status,ce1_0.tenant_id,ce1_0.local_version from sample_customer ce1_0 where ce1_0.storage_key=?;

select ce1_0.storage_key from sample_customer ce1_0 where ce1_0.storage_key=? and ce1_0.local_version=? for update of ce1_0.storage_key nowait;

update sample_customer set display_name=?,expires_at=?,review_requested_at=?,source_id=?,customer_status=?,tenant_id=?,local_version=? where storage_key=? and local_version=?;

select * from MS_STATE where work_id=?;

-- insert-race
select * from MS_WORK where work_id=? for update nowait;

select ce1_0.storage_key,ce1_0.display_name,ce1_0.expires_at,ce1_0.review_requested_at,ce1_0.source_id,ce1_0.customer_status,ce1_0.tenant_id,ce1_0.local_version from sample_customer ce1_0 where ce1_0.storage_key=?;

select ce1_0.storage_key,ce1_0.display_name,ce1_0.expires_at,ce1_0.review_requested_at,ce1_0.source_id,ce1_0.customer_status,ce1_0.tenant_id,ce1_0.local_version from sample_customer ce1_0 where ce1_0.storage_key=? for update of ce1_0.storage_key nowait;

insert into sample_customer (display_name,expires_at,review_requested_at,source_id,customer_status,tenant_id,local_version,storage_key) values (?,?,?,?,?,?,?,?) nowait;

-- jpa-lookup
select * from MS_WORK where work_id=? for update nowait;

select ce1_0.storage_key,ce1_0.display_name,ce1_0.expires_at,ce1_0.review_requested_at,ce1_0.source_id,ce1_0.customer_status,ce1_0.tenant_id,ce1_0.local_version from sample_customer ce1_0 where ce1_0.storage_key=?;

select ce1_0.storage_key from sample_customer ce1_0 where ce1_0.storage_key=? and ce1_0.local_version=? for update of ce1_0.storage_key nowait;

-- flush-extra-row
select * from MS_WORK where work_id=? for update nowait;

select ce1_0.storage_key,ce1_0.display_name,ce1_0.expires_at,ce1_0.review_requested_at,ce1_0.source_id,ce1_0.customer_status,ce1_0.tenant_id,ce1_0.local_version from sample_customer ce1_0 where ce1_0.storage_key=?;

select ce1_0.storage_key from sample_customer ce1_0 where ce1_0.storage_key=? and ce1_0.local_version=? for update of ce1_0.storage_key nowait;

update sample_customer set display_name=?,expires_at=?,review_requested_at=?,source_id=?,customer_status=?,tenant_id=?,local_version=? where storage_key=? and local_version=? nowait;
