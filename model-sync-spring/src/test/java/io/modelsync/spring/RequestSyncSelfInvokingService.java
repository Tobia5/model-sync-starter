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

public class RequestSyncSelfInvokingService {
  public void outer(String tenant, String id) {
    inner(tenant, id);
  }

  @RequestSync(binding = "customer", tenant = "#p0", key = "#p1")
  public void inner(String tenant, String id) {}
}
