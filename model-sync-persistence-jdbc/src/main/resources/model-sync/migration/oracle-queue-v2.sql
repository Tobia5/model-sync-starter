-- Queue index migration 2, additive for schema v1/v2, after queue migration 1.
-- Stop workers and review backup/DDL first. Oracle DDL commits independently.
DECLARE
  version_value VARCHAR2(64);
  recovery_count NUMBER;
  existing_count NUMBER;
BEGIN
  SELECT meta_value INTO version_value FROM MS_META WHERE meta_name='schema_version';
  IF version_value NOT IN ('1','2') THEN
    RAISE_APPLICATION_ERROR(-20001,'Queue migration requires schema v1 or v2');
  END IF;
  SELECT COUNT(*) INTO recovery_count FROM user_indexes WHERE index_name='MS_WORK_RECOVER_IX';
  IF recovery_count <> 1 THEN
    RAISE_APPLICATION_ERROR(-20002,'Apply and review queue migration 1 first');
  END IF;
  SELECT COUNT(*) INTO existing_count FROM user_indexes WHERE index_name='MS_WORK_BIND_READY_IX';
  IF existing_count <> 0 THEN
    RAISE_APPLICATION_ERROR(-20003,'Binding index exists; review its definition instead of replacing it');
  END IF;
  EXECUTE IMMEDIATE 'CREATE INDEX MS_WORK_BIND_READY_IX ON MS_WORK(binding_id,priority,work_status,next_at_ms,work_id)';
END;
/
