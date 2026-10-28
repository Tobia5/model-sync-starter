-- Explicit optional extension to ledger schema v2. Stop compliant writers for migration.
-- No FK to MS_QUOTA: acceptance must not wait for an unresolved quota-row transaction.
-- Registered groups are validated by the adapter; quota deletion while workers run is unsupported.
CREATE TABLE MS_COOLDOWN (
 operation_id VARCHAR2(64 CHAR) PRIMARY KEY,
 quota_group VARCHAR2(128 CHAR) NOT NULL,
 deadline_ms NUMBER(19) NOT NULL,
 accepted_at_ms NUMBER(19) NOT NULL,
 publication_status VARCHAR2(8 CHAR) NOT NULL CHECK (publication_status IN ('PENDING','APPLIED'))
);
CREATE INDEX MS_COOLDOWN_DUE_IX ON MS_COOLDOWN(quota_group,publication_status,deadline_ms);
CREATE INDEX MS_COOLDOWN_AGE_IX ON MS_COOLDOWN(quota_group,publication_status,accepted_at_ms,operation_id);
INSERT INTO MS_META(meta_name,meta_value) VALUES ('cooldown_schema_version','1');
