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

class RequestSyncAdvisorTest {

  private <T> T proxy(T target, CoreFixture f, Class<T> type) {
    ProxyFactory factory = new ProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAdvisor(new RequestSyncAdvisor(f.gateway, f.tx));
    return type.cast(factory.getProxy());
  }

  @Test
  void businessMutationAndIntentShareTransaction() throws Exception {
    var f = new CoreFixture();
    RequestSyncTestService service = proxy(new RequestSyncTestService(f), f, RequestSyncTestService.class);
    assertEquals("done", service.change("tenant-a", "42"));
    assertEquals(1, f.store.size());
    assertEquals("local", f.local().value());
  }

  @Test
  void checkedBusinessExceptionRollsBackBoth() {
    var f = new CoreFixture();
    RequestSyncTestService target = new RequestSyncTestService(f);
    target.fail = true;
    RequestSyncTestService service = proxy(target, f, RequestSyncTestService.class);
    assertThrows(IOException.class, () -> service.change("tenant-a", "42"));
    assertEquals(0, f.store.size());
    assertTrue(f.projection.load(f.key.subject()).isEmpty());
  }

  @Test
  void combinedTransactionalAnnotationRejected() throws Exception {
    var method = RequestSyncInvalidService.class.getMethod("doubleTransaction", String.class, String.class);
    assertThrows(
        IllegalArgumentException.class,
        () -> RequestSyncAdvisor.validate(method, RequestSyncInvalidService.class));
  }

  @Test
  void deferredReturnTypeRejected() throws Exception {
    var method = RequestSyncInvalidService.class.getMethod("asynchronous", String.class, String.class);
    assertThrows(
        IllegalArgumentException.class,
        () -> RequestSyncAdvisor.validate(method, RequestSyncInvalidService.class));
  }

  @Test
  void finalMethodRejectedAtValidation() throws Exception {
    var method = RequestSyncInvalidService.class.getMethod("finalMethod", String.class, String.class);
    assertThrows(
        IllegalArgumentException.class,
        () -> RequestSyncAdvisor.validate(method, RequestSyncInvalidService.class));
  }

  @Test
  void resultIdentityRejectedBeforeInvocation() throws Exception {
    var method = RequestSyncInvalidService.class.getMethod("resultIdentity", String.class);
    assertThrows(
        IllegalArgumentException.class,
        () -> RequestSyncAdvisor.validate(method, RequestSyncInvalidService.class));
  }

  @Test
  void selfInvocationLimitationIsExplicitlyDemonstrated() {
    var f = new CoreFixture();
    var service = proxy(new RequestSyncSelfInvokingService(), f, RequestSyncSelfInvokingService.class);
    service.outer("tenant-a", "42");
    assertEquals(0, f.store.size());
    service.inner("tenant-a", "42");
    assertEquals(1, f.store.size());
  }
}
