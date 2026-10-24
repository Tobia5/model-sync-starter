-- Queue index migration 1, additive for schema v1/v2. No data/schema-version conversion.
-- Apply with workers stopped, after backup/review. Oracle DDL commits; do not wrap in business TX.
-- A previous failed attempt may have created the index: verify its definition before resuming.
DECLARE
  version_value VARCHAR2(64);
  existing_count NUMBER;
BEGIN
  SELECT meta_value INTO version_value FROM MS_META WHERE meta_name='schema_version';
  IF version_value NOT IN ('1','2') THEN
    RAISE_APPLICATION_ERROR(-20001,'Queue migration requires schema v1 or v2');
  END IF;
  SELECT COUNT(*) INTO existing_count FROM user_indexes WHERE index_name='MS_WORK_RECOVER_IX';
  IF existing_count <> 0 THEN
    RAISE_APPLICATION_ERROR(-20002,'Recovery index exists; review its definition instead of replacing it');
  END IF;
  EXECUTE IMMEDIATE 'CREATE INDEX MS_WORK_RECOVER_IX ON MS_WORK(priority,work_status,lease_until_ms,work_id)';
END;
/
