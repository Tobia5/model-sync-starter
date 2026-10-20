CREATE TABLE MS_META (meta_name VARCHAR2(64 CHAR) PRIMARY KEY, meta_value VARCHAR2(64 CHAR) NOT NULL);
INSERT INTO MS_META (meta_name,meta_value) VALUES ('schema_version','1');
CREATE TABLE MS_WORK (
 work_id VARCHAR2(64 CHAR) PRIMARY KEY,
 binding_id VARCHAR2(128 CHAR) NOT NULL,
 tenant_id VARCHAR2(128 CHAR) NOT NULL,
 subject_id VARCHAR2(256 CHAR) NOT NULL,
 priority NUMBER(2) NOT NULL CHECK (priority BETWEEN 0 AND 3),
 generation NUMBER(19) NOT NULL,
 leased_generation NUMBER(19),
 attempts NUMBER(10) NOT NULL,
 force_refresh NUMBER(1) NOT NULL CHECK (force_refresh IN (0,1)),
 work_status VARCHAR2(16 CHAR) NOT NULL CHECK (work_status IN ('WAITING','LEASED','DEAD')),
 next_at_ms NUMBER(19) NOT NULL,
 requested_at_ms NUMBER(19) NOT NULL,
 lease_token VARCHAR2(36 CHAR),
 lease_until_ms NUMBER(19),
 last_error_code VARCHAR2(64 CHAR),
 CONSTRAINT MS_WORK_SUBJECT_UQ UNIQUE(binding_id,tenant_id,subject_id)
);
CREATE INDEX MS_WORK_READY_IX ON MS_WORK(priority,work_status,next_at_ms,work_id);
CREATE INDEX MS_WORK_LEASE_IX ON MS_WORK(work_status,lease_until_ms,priority);
CREATE TABLE MS_STATE (
 work_id VARCHAR2(64 CHAR) PRIMARY KEY REFERENCES MS_WORK(work_id),
 validated_at_ms NUMBER(19),
 reconciled_at_ms NUMBER(19),
 matched_digest VARCHAR2(256 CHAR),
 owner_validator VARCHAR2(1024 CHAR),
 outcome VARCHAR2(32 CHAR) NOT NULL,
 source_changed_at_ms NUMBER(19)
);
CREATE INDEX MS_STATE_AGE_IX ON MS_STATE(validated_at_ms,work_id);
CREATE TABLE MS_QUOTA (
 quota_group VARCHAR2(128 CHAR) PRIMARY KEY,
 spacing_ms NUMBER(19) NOT NULL CHECK (spacing_ms > 0),
 next_allowed_ms NUMBER(19) NOT NULL
);
