package example.consumer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "CONSUMER_ITEM")
public class ItemEntity {
  @Id @Column(name = "storage_key", length = 64) String key;
  @Column(name = "tenant_id", length = 128, nullable = false) String tenant;
  @Column(name = "source_id", length = 256, nullable = false) String subject;
  @Version @Column(name = "local_version", nullable = false) long revision;
  @Column(name = "item_value", length = 80) String value;
  @Column(name = "expires_at") Instant expires;
  protected ItemEntity() {}
}
