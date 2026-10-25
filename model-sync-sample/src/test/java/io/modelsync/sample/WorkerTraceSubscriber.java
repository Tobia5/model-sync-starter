package io.modelsync.sample;

import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

/** Test-only context bridge. No thread name is used as a substitute for lane identity. */
final class WorkerTraceSubscriber<T> implements CoreSubscriber<T> {
  private final CoreSubscriber<? super T> actual;
  private final WorkerTrace trace;
  WorkerTraceSubscriber(CoreSubscriber<? super T> actual, WorkerTrace trace) { this.actual = actual; this.trace = trace; }
  public Context currentContext() { return actual.currentContext(); }
  private void scoped(Runnable call) { trace.inLane(currentContext().getOrDefault("modelsync.worker.lane", -1), call); }
  public void onSubscribe(Subscription s) { scoped(() -> actual.onSubscribe(s)); }
  public void onNext(T value) { scoped(() -> actual.onNext(value)); }
  public void onError(Throwable failure) { scoped(() -> actual.onError(failure)); }
  public void onComplete() { scoped(actual::onComplete); }
}
