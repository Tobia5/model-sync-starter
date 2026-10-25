package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OracleProjectionLockSqlTest {
  @ParameterizedTest @ValueSource(strings = {
      "select p.storage_key from sample_customer p where p.storage_key=? for update nowait",
      "SELECT p.x FROM sample_customer p FOR UPDATE OF p.x NOWAIT",
      "select p.x from ms_test_runtime.sample_customer p for update of p.x,p.y nowait;",
      "select p.x from \"SAMPLE_CUSTOMER\" p FOR /* hint */ UPDATE OF p.x NOWAIT",
      "select p.x from sample_customer p\nfor update\nnowait"
  }) void acceptsProjectionNowait(String sql) { assertTrue(OracleProjectionLockSql.isNowaitSelect(sql, "sample_customer")); }

  @ParameterizedTest @ValueSource(strings = {
      "select p.x from sample_customer p for update",
      "select p.x from sample_customer p for update wait 0",
      "select p.x from sample_customer p for update skip locked",
      "select p.x from other_table p for update nowait",
      "update sample_customer set display_name='x' nowait",
      "select 'for update nowait' from sample_customer",
      "select q'[for update nowait]' from sample_customer",
      "select nq'{for update nowait}' from sample_customer",
      "select p.x from sample_customer p /* for update nowait */",
      "select p.x from sample_customer p -- for update nowait",
      "select p.x from sample_customer p for update /* nowait */",
      "select p.x from sample_customer p for update of nowait",
      "select p.x from sample_customer p for update of p.x, nowait",
      "select p.x from sample_customer p for update of 'column' nowait",
      "select p.x from sample_customer p for update of q'[column]' nowait",
      "select p.x from sample_customer p for update of p.x skip locked nowait",
      "select p.x from sample_customer p for update wait 1 nowait",
      "select p.x from sample_customer p for update nowait;select 1 from dual",
      "select \"for update nowait\" from sample_customer",
      "select 'from sample_customer' from other_table for update nowait",
      "/* select x from sample_customer for update nowait */ select 1 from dual",
      "select p.x from sample_customer p for update nowait /* unclosed"
  }) void rejectsUnqualifiedOrLiteralLocks(String sql) { assertFalse(OracleProjectionLockSql.isNowaitSelect(sql, "sample_customer")); }
}
