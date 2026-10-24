package io.modelsync.reactor;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import io.modelsync.core.*;
import io.modelsync.testkit.CoreFixture;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import static org.junit.jupiter.api.Assertions.*;

class ReactiveSyncAccessTest {
  @Test void visitIsLazyAndDoesNotCallOwner() {
    var f = new CoreFixture();
    var scheduler = Schedulers.newSingle("visit-test");
    try {
      var access = new ReactiveSyncAccess<>(new SyncTriggers(f.gateway).model(String.class), scheduler);
      var result = access.visit("tenant-a", "42", VisitPolicy.REQUIRE_FRESH);
      assertEquals(0, f.store.size());
      StepVerifier.create(result).assertNext(value -> {
        assertTrue(value.pending());
        assertTrue(Thread.currentThread().getName().startsWith("visit-test"));
      }).verifyComplete();
      assertEquals(1, f.store.size());
      assertEquals(0, f.calls.get());
    } finally { scheduler.dispose(); }
  }
  @Test void eventAdmissionIsLazyAndForced() {
    var f = new CoreFixture();
    var scheduler = Schedulers.newSingle("event-test");
    try {
      var result = ReactiveSyncAccess.accept(new SyncTriggers(f.gateway),
          SyncRequested.changed("customer", "tenant-a", "42"), scheduler);
      assertEquals(0, f.store.size());
      StepVerifier.create(result).expectNext(f.key.storageId()).verifyComplete();
      assertTrue(f.store.inspect(f.key).force());
      assertEquals(0, f.calls.get());
    } finally { scheduler.dispose(); }
  }
}
