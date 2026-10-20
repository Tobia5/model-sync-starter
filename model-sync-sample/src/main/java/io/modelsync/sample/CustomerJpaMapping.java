package io.modelsync.sample;

import io.modelsync.core.*;
import io.modelsync.jpa.JpaProjectionMapping;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
public final class CustomerJpaMapping implements JpaProjectionMapping<Customer,CustomerEntity> {
    static String storageId(SubjectKey key){return new WorkKey("customer",key).storageId();}
    @Override public Optional<CustomerEntity> find(EntityManager em,SubjectKey key,LockModeType lock){
        CustomerEntity e=em.find(CustomerEntity.class,storageId(key),lock);
        if(e!=null && (!e.tenant.equals(key.tenant()) || !e.sourceId.equals(key.id())))throw new IllegalStateException("Tenant/subject mismatch");
        return Optional.ofNullable(e);
    }
    @Override public Projection<Customer> snapshot(CustomerEntity e){
        return new Projection<>(new Customer(e.displayName,e.status),Long.toString(e.version),e.expiresAt);
    }
    @Override public CustomerEntity create(SubjectKey key,Customer value,Instant expires){
        CustomerEntity e=new CustomerEntity();e.storageKey=storageId(key);e.tenant=key.tenant();e.sourceId=key.id();update(e,value,expires);return e;
    }
    @Override public void update(CustomerEntity e,Customer value,Instant expires){
        e.displayName=value.displayName();e.status=value.status();e.expiresAt=expires;
        // Never overwrite reviewRequestedAt: that field belongs to this application.
    }
}
