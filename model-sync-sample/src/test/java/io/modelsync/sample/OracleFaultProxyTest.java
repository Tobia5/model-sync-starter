package io.modelsync.sample;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OracleFaultProxyTest {
  @Test void acceptsBothServiceUrlFormsAndRejectsAmbiguousOrCredentialBearingTargets() {
    var expected = OracleFaultProxy.upstream("jdbc:oracle:thin:@127.0.0.1:55555/FREEPDB1");
    assertEquals(expected, OracleFaultProxy.upstream("jdbc:oracle:thin:@//127.0.0.1:55555/FREEPDB1"));
    for (String invalid : new String[] {"http://127.0.0.1:55555/FREEPDB1", "jdbc:oracle:thin:@//user:secret@127.0.0.1:55555/FREEPDB1",
        "jdbc:oracle:thin:@//127.0.0.1:0/FREEPDB1", "jdbc:oracle:thin:@//127.0.0.1:55555/FREEPDB1?hidden=secret"})
      assertThrows(IllegalStateException.class, () -> OracleFaultProxy.upstream(invalid));
  }
}
