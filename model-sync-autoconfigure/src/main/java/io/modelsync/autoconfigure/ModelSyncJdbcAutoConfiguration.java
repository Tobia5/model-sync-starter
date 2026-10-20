package io.modelsync.autoconfigure;

import io.modelsync.core.*;
import io.modelsync.jdbc.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;
import java.time.Clock;
@AutoConfiguration(afterName={
    "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"})
@ConditionalOnClass(JdbcWorkStore.class)
@ConditionalOnSingleCandidate(DataSource.class)
@ConditionalOnProperty(prefix="model-sync",name="enabled",havingValue="true")
@EnableConfigurationProperties(ModelSyncProperties.class)
public class ModelSyncJdbcAutoConfiguration {
    @Bean(name="modelSyncClock")
    @ConditionalOnMissingBean(name="modelSyncClock")
    Clock modelSyncClock(DataSource dataSource){return new DatabaseClock(new JdbcTemplate(dataSource));}
    @Bean
    @ConditionalOnMissingBean(WorkStore.class)
    @ConditionalOnSingleCandidate(PlatformTransactionManager.class)
    JdbcWorkStore modelSyncWorkStore(DataSource dataSource,PlatformTransactionManager transactionManager){
        validateTransactionDataSource(dataSource,transactionManager);
        JdbcWorkStore store=new JdbcWorkStore(new JdbcTemplate(dataSource));store.verifySchema();return store;
    }
    @Bean
    @ConditionalOnMissingBean(QuotaGate.class)
    @ConditionalOnSingleCandidate(PlatformTransactionManager.class)
    JdbcQuotaGate modelSyncQuotaGate(DataSource dataSource,PlatformTransactionManager manager,
            @Qualifier("modelSyncClock") Clock clock,ModelSyncProperties properties,ModelRegistry registry){
        validateTransactionDataSource(dataSource,manager);
        JdbcQuotaGate gate=new JdbcQuotaGate(new JdbcTemplate(dataSource),manager,clock);
        properties.getQuotas().forEach(gate::register);
        for(ModelBinding<?> b:registry.all())
            if(!properties.getQuotas().containsKey(b.quota()))throw new IllegalStateException("No quota configured for binding: "+b.id());
        return gate;
    }
    private static void validateTransactionDataSource(DataSource dataSource,PlatformTransactionManager manager){
        // JPA/JDBC managers expose getDataSource. Reflection avoids forcing spring-orm into a JDBC-only app.
        try{
            var method=manager.getClass().getMethod("getDataSource");Object configured=method.invoke(manager);
            if(configured!=dataSource)throw new IllegalStateException("Model Sync ledger must use the transaction manager's DataSource");
        }catch(NoSuchMethodException unsupported){
            throw new IllegalStateException("Provide custom WorkStore/QuotaGate beans for an XA or custom transaction manager",unsupported);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot validate transaction DataSource",failure);}
    }
}
