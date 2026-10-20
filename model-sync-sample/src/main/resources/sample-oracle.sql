-- Sample domain table only. The production application owns its own projection schema.
CREATE TABLE SAMPLE_CUSTOMER (
 storage_key VARCHAR2(64 CHAR) PRIMARY KEY,
 tenant_id VARCHAR2(128 CHAR) NOT NULL,
 source_id VARCHAR2(256 CHAR) NOT NULL,
 local_version NUMBER(19) NOT NULL,
 display_name VARCHAR2(200 CHAR),
 customer_status VARCHAR2(64 CHAR),
 expires_at TIMESTAMP(6) WITH TIME ZONE,
 review_requested_at TIMESTAMP(6) WITH TIME ZONE,
 CONSTRAINT SAMPLE_CUST_SUBJECT_UQ UNIQUE(tenant_id,source_id)
);
