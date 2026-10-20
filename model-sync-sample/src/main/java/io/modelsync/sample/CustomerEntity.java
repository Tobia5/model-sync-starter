package io.modelsync.sample;

import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="SAMPLE_CUSTOMER",uniqueConstraints=@UniqueConstraint(name="SAMPLE_CUST_SUBJECT_UQ",columnNames={"tenant_id","source_id"}))
public class CustomerEntity {
    @Id @Column(name="storage_key",length=64) String storageKey;
    @Column(name="tenant_id",nullable=false,length=128) String tenant;
    @Column(name="source_id",nullable=false,length=256) String sourceId;
    @Version @Column(name="local_version",nullable=false) long version;
    @Column(name="display_name",length=200) String displayName;
    @Column(name="customer_status",length=64) String status;
    @Column(name="expires_at") Instant expiresAt;
    // Locally owned field: deliberately excluded from the synchronized Customer DTO.
    @Column(name="review_requested_at") Instant reviewRequestedAt;
    protected CustomerEntity(){}
}
