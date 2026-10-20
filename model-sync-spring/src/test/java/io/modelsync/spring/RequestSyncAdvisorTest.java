package io.modelsync.spring;

import io.modelsync.core.*;
import io.modelsync.testkit.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
class RequestSyncAdvisorTest {
    public static class Service {
        final CoreFixture f;boolean fail;
        Service(CoreFixture f){this.f=f;}
        @RequestSync(binding="customer",tenant="#p0",key="#p1")
        public String change(String tenant,String id) throws IOException {
            assertTrue(f.tx.active());
            assertEquals(1,f.store.size(),"Intent is locked before business projection mutation");
            f.projection.compareAndSet(new SubjectKey(tenant,id),null,"local",null);
            if(fail)throw new IOException("checked business failure");return "done";
        }
    }
    private <T> T proxy(T target,CoreFixture f,Class<T> type){
        ProxyFactory factory=new ProxyFactory(target);factory.setProxyTargetClass(true);
        factory.addAdvisor(new RequestSyncAdvisor(f.gateway,f.tx));return type.cast(factory.getProxy());
    }
    @Test void businessMutationAndIntentShareTransaction() throws Exception {
        var f=new CoreFixture();Service service=proxy(new Service(f),f,Service.class);
        assertEquals("done",service.change("tenant-a","42"));
        assertEquals(1,f.store.size());assertEquals("local",f.local().value());
    }
    @Test void checkedBusinessExceptionRollsBackBoth() {
        var f=new CoreFixture();Service target=new Service(f);target.fail=true;Service service=proxy(target,f,Service.class);
        assertThrows(IOException.class,()->service.change("tenant-a","42"));
        assertEquals(0,f.store.size());assertTrue(f.projection.load(f.key.subject()).isEmpty());
    }
    public static class InvalidService {
        @RequestSync(binding="customer",tenant="#p0",key="#p1")
        @Transactional
        public void doubleTransaction(String tenant,String id){}
        @RequestSync(binding="customer",tenant="#p0",key="#p1")
        public CompletableFuture<Void> asynchronous(String tenant,String id){throw new AssertionError("must not execute");}
        @RequestSync(binding="customer",tenant="#p0",key="#p1")
        public final void finalMethod(String tenant,String id){}
        @RequestSync(binding="customer",tenant="#p0",key="#result")
        public String resultIdentity(String tenant){return "1";}
    }
    @Test void combinedTransactionalAnnotationRejected() throws Exception {
        var method=InvalidService.class.getMethod("doubleTransaction",String.class,String.class);
        assertThrows(IllegalArgumentException.class,()->RequestSyncAdvisor.validate(method,InvalidService.class));
    }
    @Test void deferredReturnTypeRejected() throws Exception {
        var method=InvalidService.class.getMethod("asynchronous",String.class,String.class);
        assertThrows(IllegalArgumentException.class,()->RequestSyncAdvisor.validate(method,InvalidService.class));
    }
    @Test void finalMethodRejectedAtValidation() throws Exception {
        var method=InvalidService.class.getMethod("finalMethod",String.class,String.class);
        assertThrows(IllegalArgumentException.class,()->RequestSyncAdvisor.validate(method,InvalidService.class));
    }
    @Test void resultIdentityRejectedBeforeInvocation() throws Exception {
        var method=InvalidService.class.getMethod("resultIdentity",String.class);
        assertThrows(IllegalArgumentException.class,()->RequestSyncAdvisor.validate(method,InvalidService.class));
    }
    public static class SelfInvoking {
        public void outer(String tenant,String id){inner(tenant,id);}
        @RequestSync(binding="customer",tenant="#p0",key="#p1")
        public void inner(String tenant,String id){}
    }
    @Test void selfInvocationLimitationIsExplicitlyDemonstrated(){
        var f=new CoreFixture();var service=proxy(new SelfInvoking(),f,SelfInvoking.class);
        service.outer("tenant-a","42");assertEquals(0,f.store.size());
        service.inner("tenant-a","42");assertEquals(1,f.store.size());
    }
}
