-- OPERATOR-REVIEWED MAINTENANCE SCRIPT, NOT AN AUTOMATIC STARTUP MIGRATION.
-- Read docs/PRODUCTION.md before execution. Oracle verification is a release gate.
-- Stop ALL old/new workers AND enqueue writers first. Back up schema/data.
-- Oracle DDL commits implicitly: this script cannot be rolled back as one transaction.
-- SQL*Plus / SQLcl invocation in the application schema; no substitution variables.
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET SERVEROUTPUT ON
DECLARE
  version_value VARCHAR2(64);
  old_constraint VARCHAR2(128);
  candidates PLS_INTEGER := 0;
  new_name_count PLS_INTEGER;
  active_leases PLS_INTEGER;
BEGIN
  SELECT meta_value INTO version_value FROM MS_META WHERE meta_name='schema_version';
  IF version_value <> '1' THEN
    RAISE_APPLICATION_ERROR(-20001, 'Expected schema version 1; inspect state before continuing');
  END IF;
  SELECT COUNT(*) INTO active_leases FROM MS_WORK WHERE work_status='LEASED';
  IF active_leases <> 0 THEN
    RAISE_APPLICATION_ERROR(-20002, 'LEASED work remains; resolve via approved recovery before maintenance');
  END IF;
  SELECT COUNT(*) INTO new_name_count FROM USER_CONSTRAINTS WHERE constraint_name='MS_WORK_STATUS_CK';
  IF new_name_count <> 0 THEN
    RAISE_APPLICATION_ERROR(-20003, 'Target constraint already exists; inspect interrupted migration');
  END IF;
  -- Match only the exact, normalized v1 status predicate. Never drop all CHECK constraints.
  FOR c IN (SELECT constraint_name, search_condition_vc, status, validated
            FROM USER_CONSTRAINTS WHERE table_name='MS_WORK' AND constraint_type='C') LOOP
    IF REGEXP_REPLACE(UPPER(c.search_condition_vc), '[[:space:]"()]', '') =
       'WORK_STATUSIN''WAITING'',''LEASED'',''DEAD''' THEN
      IF c.status <> 'ENABLED' OR c.validated <> 'VALIDATED' THEN
        RAISE_APPLICATION_ERROR(-20004, 'Source status constraint is not enabled and validated');
      END IF;
      candidates := candidates + 1;
      old_constraint := c.constraint_name;
    END IF;
  END LOOP;
  IF candidates <> 1 THEN
    RAISE_APPLICATION_ERROR(-20005, 'Expected exactly one original v1 status constraint; DBA review required');
  END IF;
  -- Add the replacement FIRST. If a later step fails, an enabled status check remains.
  EXECUTE IMMEDIATE 'ALTER TABLE MS_WORK ADD CONSTRAINT MS_WORK_STATUS_CK ' ||
                    'CHECK (work_status IN (''WAITING'',''LEASED'',''DEAD'',''IDLE'')) ENABLE VALIDATE';
  EXECUTE IMMEDIATE 'ALTER TABLE MS_WORK DROP CONSTRAINT ' || DBMS_ASSERT.ENQUOTE_NAME(old_constraint, FALSE);
  UPDATE MS_META SET meta_value='2' WHERE meta_name='schema_version' AND meta_value='1';
  IF SQL%ROWCOUNT <> 1 THEN
    RAISE_APPLICATION_ERROR(-20006, 'Schema version changed unexpectedly; keep services stopped');
  END IF;
  COMMIT;
  DBMS_OUTPUT.PUT_LINE('Schema version 2 recorded. Verify status constraint and run smoke tests before enabling work.');
END;
/
SELECT meta_name,meta_value FROM MS_META WHERE meta_name='schema_version';
SELECT constraint_name,status,validated,search_condition_vc FROM USER_CONSTRAINTS
 WHERE table_name='MS_WORK' AND constraint_name='MS_WORK_STATUS_CK';
