package io.modelsync.core.application;

import io.modelsync.core.WorkLease;
import io.modelsync.core.port.QuotaGate;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Avoids known cooldowns and same-process claim stampedes without spending provider quota.
 * Slots cover claim/prepare only, expire with the work lease, and never suppress recovery.
 * Other processes still race through authoritative distributed admission before owner I/O.
 */
final class ProviderClaimScheduler {
  private final Map<String, String> providers;
  private final Map<String, AtomicReference<ProviderClaimReservation>> slots;
  private final QuotaGate gate;

  ProviderClaimScheduler(ModelRegistry registry, QuotaGate gate) {
    Map<String, String> bindings = new LinkedHashMap<>();
    Map<String, AtomicReference<ProviderClaimReservation>> lanes = new LinkedHashMap<>();
    registry.all().forEach(b -> {
      bindings.put(b.id(), b.quota());
      lanes.computeIfAbsent(b.quota(), group -> new AtomicReference<>());
    });
    this.providers = Map.copyOf(bindings);
    this.slots = Map.copyOf(lanes);
    this.gate = gate;
  }

  ClaimSchedule schedule(Instant now, Duration lifetime) {
    Map<String, ProviderClaimReservation> owned = new LinkedHashMap<>();
    Set<String> available = new HashSet<>();
    Set<String> known = new HashSet<>();
    // No blocking acquisition: an occupied/cooling provider cannot hold up another provider.
    try {
    slots.forEach((group, slot) -> {
      Optional<Instant> hint = gate.nextAdmission(group);
      if (hint.isEmpty()) { available.add(group); return; }
      known.add(group);
      if (now.isBefore(hint.get())) return;
      var before = slot.get();
      if (before != null && now.isBefore(before.until())) return;
      var reservation = new ProviderClaimReservation(UUID.randomUUID().toString(), now.plus(lifetime));
      if (slot.compareAndSet(before, reservation)) {
        owned.put(group, reservation);
        // Recheck AFTER taking the local slot. A hint read before another local prepare
        // completed must not cause another expensive selection using that stale snapshot.
        Optional<Instant> rechecked = gate.nextAdmission(group);
        if (rechecked.isPresent() && now.isBefore(rechecked.get())) {
          owned.remove(group);
          slot.compareAndSet(reservation, null);
        } else available.add(group);
      }
    });
    } catch (RuntimeException | Error failure) {
      owned.forEach((group, reservation) -> slots.get(group).compareAndSet(reservation, null));
      throw failure;
    }
    Set<String> bindings = new LinkedHashSet<>();
    providers.forEach((binding, group) -> { if (available.contains(group)) bindings.add(binding); });
    return new ClaimSchedule(bindings.size() == providers.size() && known.isEmpty() ? Optional.empty()
        : Optional.of(Set.copyOf(bindings)), Map.copyOf(owned));
  }

  void claimed(ClaimSchedule schedule, Optional<WorkLease> result) {
    String selected = result.map(lease -> providers.get(lease.key().binding())).orElse(null);
    schedule.reservations().forEach((group, reservation) -> {
      var replacement = Objects.equals(group, selected)
          ? new ProviderClaimReservation(result.orElseThrow().token(), result.orElseThrow().expiresAt()) : null;
      slots.get(group).compareAndSet(reservation, replacement);
    });
  }

  void release(WorkLease lease) {
    String group = providers.get(lease.key().binding());
    if (group == null) return;
    var slot = slots.get(group);
    var reservation = slot.get();
    // An old completion/failure must never release a replacement poll's reservation.
    if (reservation != null && reservation.token().equals(lease.token())) slot.compareAndSet(reservation, null);
  }
}
