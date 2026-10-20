package io.modelsync.sample;

import io.modelsync.core.*;
import io.modelsync.spring.RequestSync;
import jakarta.persistence.*;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.stereotype.Service;
import java.time.Instant;
@Service
public class CustomerService {
    private final EntityManager em;
    public CustomerService(EntityManagerFactory emf){em=SharedEntityManagerCreator.createSharedEntityManager(emf);}
    /** The aspect supplies REQUIRED local transaction semantics. No @Transactional and no REST here. */
    @RequestSync(binding="customer",tenant="#p0",key="#p1",priority=Priority.HIGH,force=true)
    public void requestReview(String tenant,String subjectId){
        var entity=new CustomerJpaMapping().find(em,new SubjectKey(tenant,subjectId),LockModeType.PESSIMISTIC_WRITE).orElseThrow();
        entity.reviewRequestedAt=Instant.now();
    }
}
