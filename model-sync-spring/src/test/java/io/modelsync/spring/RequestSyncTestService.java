package io.modelsync.spring;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import static org.junit.jupiter.api.Assertions.*;
import io.modelsync.core.*;
import io.modelsync.testkit.*;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.Transactional;

public class RequestSyncTestService {
  final CoreFixture f;
  boolean fail;

  RequestSyncTestService(CoreFixture f) {
    this.f = f;
  }

  @RequestSync(binding = "customer", tenant = "#p0", key = "#p1")
  public String change(String tenant, String id) throws IOException {
    assertTrue(f.tx.active());
    assertEquals(1, f.store.size(), "Intent is locked before business projection mutation");
    f.projection.compareAndSet(new SubjectKey(tenant, id), null, "local", null);
    if (fail) throw new IOException("checked business failure");
    return "done";
  }
}
