-- Fresh schema only. Existing v1 installations must use the reviewed migration procedure.
CREATE TABLE MS_META (meta_name VARCHAR(64) PRIMARY KEY, meta_value VARCHAR(64) NOT NULL);
INSERT INTO MS_META (meta_name,meta_value) VALUES ('schema_version','2');
CREATE TABLE MS_WORK (
 work_id VARCHAR(64) PRIMARY KEY,
 binding_id VARCHAR(128) NOT NULL,
 tenant_id VARCHAR(128) NOT NULL,
 subject_id VARCHAR(256) NOT NULL,
 priority INTEGER NOT NULL CHECK (priority BETWEEN 0 AND 3),
 generation BIGINT NOT NULL,
 leased_generation BIGINT,
 attempts INTEGER NOT NULL,
 force_refresh INTEGER NOT NULL CHECK (force_refresh BETWEEN 0 AND 1),
 work_status VARCHAR(16) NOT NULL CONSTRAINT MS_WORK_STATUS_CK CHECK (CASE work_status WHEN 'WAITING' THEN TRUE WHEN 'LEASED' THEN TRUE WHEN 'DEAD' THEN TRUE WHEN 'IDLE' THEN TRUE ELSE FALSE END),
 next_at_ms BIGINT NOT NULL,
 requested_at_ms BIGINT NOT NULL,
 lease_token VARCHAR(36),
 lease_until_ms BIGINT,
 last_error_code VARCHAR(64),
 CONSTRAINT MS_WORK_SUBJECT_UQ UNIQUE(binding_id,tenant_id,subject_id)
);
CREATE INDEX MS_WORK_READY_IX ON MS_WORK(priority,work_status,next_at_ms,work_id);
CREATE INDEX MS_WORK_LEASE_IX ON MS_WORK(work_status,lease_until_ms,priority);
CREATE INDEX MS_WORK_RECOVER_IX ON MS_WORK(priority,work_status,lease_until_ms,work_id);
CREATE TABLE MS_STATE (
 work_id VARCHAR(64) PRIMARY KEY REFERENCES MS_WORK(work_id),
 validated_at_ms BIGINT,
 reconciled_at_ms BIGINT,
 matched_digest VARCHAR(256),
 owner_validator VARCHAR(1024),
 outcome VARCHAR(32) NOT NULL,
 source_changed_at_ms BIGINT
);
CREATE INDEX MS_STATE_AGE_IX ON MS_STATE(validated_at_ms,work_id);
CREATE TABLE MS_QUOTA (
 quota_group VARCHAR(128) PRIMARY KEY,
 spacing_ms BIGINT NOT NULL CHECK (spacing_ms > 0),
 next_allowed_ms BIGINT NOT NULL
);
