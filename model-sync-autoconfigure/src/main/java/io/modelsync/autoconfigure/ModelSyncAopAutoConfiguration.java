package io.modelsync.autoconfigure;

import io.modelsync.core.*;
import io.modelsync.spring.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
@AutoConfiguration(after=ModelSyncAutoConfiguration.class)
@ConditionalOnBean(SyncGateway.class)
@ConditionalOnProperty(prefix="model-sync.aop",name="enabled",havingValue="true")
@EnableAspectJAutoProxy
public class ModelSyncAopAutoConfiguration {
    @Bean
    static RequestSyncAnnotationValidator modelSyncAnnotationValidator(){return new RequestSyncAnnotationValidator();}
    @Bean @ConditionalOnMissingBean(RequestSyncAdvisor.class)
    RequestSyncAdvisor modelSyncRequestAdvisor(SyncGateway gateway,LocalTransactions tx){return new RequestSyncAdvisor(gateway,tx);}
}
