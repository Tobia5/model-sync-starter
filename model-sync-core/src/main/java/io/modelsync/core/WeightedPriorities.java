package io.modelsync.core;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Work-conserving weighted priority selection.
 *
 * <p>The policy controls which priority queue is inspected next. It does not cancel in-flight owner
 * requests or override quota admission.
 */
public final class WeightedPriorities {
  private final Priority[] wheel;
  private final AtomicInteger cursor = new AtomicInteger();

  /**
   * Creates a weighted priority policy.
   *
   * @param urgent relative weight for urgent work
   * @param high relative weight for high-priority work
   * @param normal relative weight for normal work
   * @param background relative weight for background work
   */
  public WeightedPriorities(int urgent, int high, int normal, int background) {
    int[] w = {urgent, high, normal, background};
    for (int n : w)
      if (n < 1 || n > 1000) throw new IllegalArgumentException("Weight must be 1..1000");
    wheel = new Priority[urgent + high + normal + background];
    int p = 0;
    for (int i = 0; i < w.length; i++)
      for (int j = 0; j < w[i]; j++) wheel[p++] = Priority.values()[i];
  }

  /**
   * Returns the next priority to inspect.
   *
   * @return next priority bucket
   */
  public Priority next() {
    return wheel[Math.floorMod(cursor.getAndIncrement(), wheel.length)];
  }

  /**
   * Returns default priority weights.
   *
   * @return default policy favoring urgent and high-priority work
   */
  public static WeightedPriorities defaults() {
    return new WeightedPriorities(4, 3, 2, 1);
  }
}
