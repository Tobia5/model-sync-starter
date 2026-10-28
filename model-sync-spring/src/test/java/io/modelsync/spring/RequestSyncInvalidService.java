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

public class RequestSyncInvalidService {
  @RequestSync(binding = "customer", tenant = "#p0", key = "#p1")
  @Transactional
  public void doubleTransaction(String tenant, String id) {}

  @RequestSync(binding = "customer", tenant = "#p0", key = "#p1")
  public CompletableFuture<Void> asynchronous(String tenant, String id) {
    throw new AssertionError("must not execute");
  }

  @RequestSync(binding = "customer", tenant = "#p0", key = "#p1")
  public final void finalMethod(String tenant, String id) {}

  @RequestSync(binding = "customer", tenant = "#p0", key = "#result")
  public String resultIdentity(String tenant) {
    return "1";
  }
}
