package io.modelsync.testkit;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

/** Mutable UTC clock for deterministic tests. */
public final class MutableClock extends Clock {
  private final AtomicReference<Instant> now;
  private final ZoneId zone;

  public MutableClock(Instant instant) {
    this(new AtomicReference<>(instant), ZoneOffset.UTC);
  }

  private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
    this.now = now;
    this.zone = zone;
  }

  public void advance(Duration duration) {
    now.updateAndGet(value -> value.plus(duration));
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return new MutableClock(now, zone);
  }

  @Override
  public Instant instant() {
    return now.get();
  }
}
