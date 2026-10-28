package io.modelsync.reactor;

import io.modelsync.core.QuotaAdmission;
import io.modelsync.core.port.*;
import java.time.Instant;
import java.util.Optional;

/** Policy fake, explicitly not a durable database/process qualification. */
final class WorkerCooldownGate implements QuotaGate {
  private final QuotaGate delegate;
  boolean refuse;
  WorkerCooldownGate(QuotaGate delegate,boolean refuse){this.delegate=delegate;this.refuse=refuse;}
  public QuotaAdmission acquire(String group){return delegate.acquire(group);}
  public Optional<Instant> nextAdmission(String group){return delegate.nextAdmission(group);}
  public void cooldown(String group,Instant until){delegate.cooldown(group,until);}
  public CooldownReceipt publishCooldown(CooldownRequest request) {
    if(refuse)throw new LocalContention("synthetic acceptance",null);
    delegate.cooldown(request.group(),request.until());return new CooldownReceipt(request,CooldownState.DURABLE_PENDING,true);
  }
}
