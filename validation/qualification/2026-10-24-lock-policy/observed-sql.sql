-- disabled-compatible
select ce1_0.storage_key,ce1_0.display_name,ce1_0.expires_at,ce1_0.review_requested_at,ce1_0.source_id,ce1_0.customer_status,ce1_0.tenant_id,ce1_0.local_version from sample_customer ce1_0 where ce1_0.storage_key=?;
select ce1_0.storage_key from sample_customer ce1_0 where ce1_0.storage_key=? and ce1_0.local_version=? for update of ce1_0.storage_key;
update sample_customer set display_name=?,expires_at=?,review_requested_at=?,source_id=?,customer_status=?,tenant_id=?,local_version=? where storage_key=? and local_version=?;

-- producer-refusal
select * from MS_WORK where work_id=:1 for update nowait;

-- uncontended-first-inserts
select ce1_0.storage_key,ce1_0.display_name,ce1_0.expires_at,ce1_0.review_requested_at,ce1_0.source_id,ce1_0.customer_status,ce1_0.tenant_id,ce1_0.local_version from sample_customer ce1_0 where ce1_0.storage_key=?;
select ce1_0.storage_key,ce1_0.display_name,ce1_0.expires_at,ce1_0.review_requested_at,ce1_0.source_id,ce1_0.customer_status,ce1_0.tenant_id,ce1_0.local_version from sample_customer ce1_0 where ce1_0.storage_key=? for update of ce1_0.storage_key nowait;
insert into sample_customer (display_name,expires_at,review_requested_at,source_id,customer_status,tenant_id,local_version,storage_key) values (?,?,?,?,?,?,?,?);

-- actual-worker-contention-exhaustion
select ce1_0.storage_key,ce1_0.display_name,ce1_0.expires_at,ce1_0.review_requested_at,ce1_0.source_id,ce1_0.customer_status,ce1_0.tenant_id,ce1_0.local_version from sample_customer ce1_0 where ce1_0.storage_key=?;
select ce1_0.storage_key from sample_customer ce1_0 where ce1_0.storage_key=? and ce1_0.local_version=? for update of ce1_0.storage_key nowait;

-- enabled-safety-controls
select ce1_0.storage_key,ce1_0.display_name,ce1_0.expires_at,ce1_0.review_requested_at,ce1_0.source_id,ce1_0.customer_status,ce1_0.tenant_id,ce1_0.local_version from sample_customer ce1_0 where ce1_0.storage_key=?;
select ce1_0.storage_key from sample_customer ce1_0 where ce1_0.storage_key=? and ce1_0.local_version=? for update of ce1_0.storage_key nowait;
update sample_customer set display_name=?,expires_at=?,review_requested_at=?,source_id=?,customer_status=?,tenant_id=?,local_version=? where storage_key=? and local_version=?;

-- result-refusal-recovery
select ce1_0.storage_key from sample_customer ce1_0 where ce1_0.storage_key=:1 and ce1_0.local_version=:2 for update of ce1_0.storage_key nowait;
select ce1_0.storage_key,ce1_0.display_name,ce1_0.expires_at,ce1_0.review_requested_at,ce1_0.source_id,ce1_0.customer_status,ce1_0.tenant_id,ce1_0.local_version from sample_customer ce1_0 where ce1_0.storage_key=?;
select ce1_0.storage_key from sample_customer ce1_0 where ce1_0.storage_key=? and ce1_0.local_version=? for update of ce1_0.storage_key nowait;
update sample_customer set display_name=?,expires_at=?,review_requested_at=?,source_id=?,customer_status=?,tenant_id=?,local_version=? where storage_key=? and local_version=?;

-- retry-metadata-refusal
select * from MS_WORK where work_id=:1 for update nowait;
select ce1_0.storage_key,ce1_0.display_name,ce1_0.expires_at,ce1_0.review_requested_at,ce1_0.source_id,ce1_0.customer_status,ce1_0.tenant_id,ce1_0.local_version from sample_customer ce1_0 where ce1_0.storage_key=?;
select ce1_0.storage_key from sample_customer ce1_0 where ce1_0.storage_key=? and ce1_0.local_version=? for update of ce1_0.storage_key nowait;
update sample_customer set display_name=?,expires_at=?,review_requested_at=?,source_id=?,customer_status=?,tenant_id=?,local_version=? where storage_key=? and local_version=?;
