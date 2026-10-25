package io.modelsync.jdbc;

import io.modelsync.core.port.LocalContention;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.function.LongConsumer;

/** Four fresh transactions, at most 70 ms total jittered sleep. I/O bounds remain separate. */
final class CooldownRetry {
  private final DoubleSupplier jitter;
  private final LongConsumer pause;
  CooldownRetry(DoubleSupplier jitter, LongConsumer pause) { this.jitter = jitter; this.pause = pause; }
  <T> T run(Supplier<T> attempt) {
    for (int number = 0; ; number++) {
      try { return attempt.get(); }
      catch (LocalContention refusal) {
        if (number == 3 || Thread.currentThread().isInterrupted()) throw refusal;
        JdbcCooldownPublications.requireOutsideTransaction();
        long cap = 10L << number;
        double value=jitter.getAsDouble();
        if(!Double.isFinite(value))throw new IllegalStateException("Invalid cooldown jitter");
        pause.accept(Math.max(1, (long)(cap * (.5 + .5 * Math.clamp(value, 0, 1)))));
      }
    }
  }
  static void sleep(long millis) {
    try { Thread.sleep(millis); }
    catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt(); throw new CooldownRetryInterrupted();
    }
  }
}
