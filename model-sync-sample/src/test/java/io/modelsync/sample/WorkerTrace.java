package io.modelsync.sample;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.core.scheduler.Schedulers;

/** Bounded run-local test observer. Keeps primitive snapshots, never invocations or exception graphs. */
final class WorkerTrace implements AutoCloseable {
  private final boolean enabled;
  private final String hook = "modelsync-diagnostic-" + UUID.randomUUID();
  private final ThreadLocal<Integer> lane = new ThreadLocal<>();
  private final ThreadLocal<Map<String, Long>> scheduleHints = new ThreadLocal<>();
  private final List<Map<String, Object>> events = new ArrayList<>();
  private final AtomicInteger dropped = new AtomicInteger();
  private volatile boolean recording;
  WorkerTrace(boolean enabled) {
    this.enabled = enabled;
    if (enabled) {
      Hooks.onEachOperator(hook, Operators.lift((scannable, subscriber) -> new WorkerTraceSubscriber<>(subscriber, this)));
      Schedulers.onScheduleHook(hook, task -> {
        int id = lane(); long scheduled = System.nanoTime();
        return () -> inLane(id, () -> { event("scheduler-wakeup", Map.of("scheduled_ns", scheduled)); task.run(); });
      });
    }
  }
  boolean enabled() { return enabled; }
  int lane() { Integer id = lane.get(); return id == null ? -1 : id; }
  void inLane(int id, Runnable work) {
    Integer previous = lane.get(); if (id >= 0) lane.set(id);
    try { work.run(); } finally { if (previous == null) lane.remove(); else lane.set(previous); }
  }
  synchronized void begin() { events.clear(); dropped.set(0); recording = true; }
  synchronized Set<Integer> claimLanes() {
    var result = new HashSet<Integer>();
    events.stream().filter(e -> e.get("event").equals("coordinator-claim")).forEach(e -> result.add(((Number)e.get("lane")).intValue()));
    return result;
  }
  synchronized Map<String, Object> end() {
    recording = false;
    return Map.of("events", List.copyOf(events), "dropped", dropped.get(), "limit", 40000,
        "unattributed_coordinator_calls", events.stream().filter(e -> e.get("event").equals("coordinator-claim") && ((Number)e.get("lane")).intValue()<0).count());
  }
  synchronized void event(String event, Map<String, Object> values) {
    if (!recording) return;
    if (events.size() == 40000) { dropped.incrementAndGet(); return; }
    var record = new LinkedHashMap<String, Object>(); record.put("event", event); record.put("lane", lane());
    record.put("ns", System.nanoTime()); record.put("clock_ms", System.currentTimeMillis()); record.putAll(values); events.add(record);
  }
  Object coordinator(Object bean) {
    Object scheduling = ReflectionTestUtils.getField(bean, "scheduling");
    ReflectionTestUtils.setField(bean, "scheduling", spy(scheduling, "reservation"));
    return spy(bean, "coordinator");
  }
  @SuppressWarnings({"unchecked", "rawtypes"}) Object spy(Object bean, String role) {
    return Mockito.mock((Class)bean.getClass(), Mockito.withSettings().spiedInstance(bean)
        .defaultAnswer(invocation -> invoke(bean, role, invocation)));
  }
  private Object invoke(Object bean, String role, InvocationOnMock invocation) throws Throwable {
    String method = invocation.getMethod().getName(); long began = System.nanoTime();
    if (!(role.equals("coordinator") && Set.of("claim", "prepare", "finish", "failed").contains(method)
        || role.equals("reservation") && Set.of("schedule", "claimed", "release").contains(method)
        || role.equals("quota") && Set.of("nextAdmission", "acquire", "publishCooldown", "cooldown").contains(method)
        || role.equals("store") && Set.of("claim", "complete", "completeOnDemand", "reschedule").contains(method)))
      return invocation.callRealMethod();
    Map<String, Object> values = new LinkedHashMap<>(); values.put("began_ns", began);
    Map<String, Long> previousHints = scheduleHints.get();
    boolean scheduling = role.equals("reservation") && method.equals("schedule");
    if (scheduling) {
      scheduleHints.set(new LinkedHashMap<>());
      values.put("schedule_clock_ms", ((Instant)invocation.getArgument(0)).toEpochMilli());
    }
    if (role.equals("reservation")) values.put("before", slots(bean));
    if (role.equals("store") && method.equals("claim")) {
      values.put("priority", invocation.getArgument(0).toString());
      values.put("claim_clock_ms", ((Instant)invocation.getArgument(1)).toEpochMilli());
      if (invocation.getArguments().length == 4) values.put("eligible_bindings", invocation.getArgument(3));
    }
    if (role.equals("quota")) {
      Object argument = invocation.getArgument(0);
      values.put("group", argument instanceof io.modelsync.core.port.CooldownRequest request ? request.group() : String.valueOf(argument));
    }
    try {
      Object result = invocation.callRealMethod();
      if (result instanceof Optional<?> optional) {
        values.put("present", optional.isPresent());
        if (optional.orElse(null) instanceof Instant deadline) values.put("hint_ms", deadline.toEpochMilli());
        if (role.equals("quota") && method.equals("nextAdmission") && scheduleHints.get()!=null && optional.orElse(null) instanceof Instant deadline)
          scheduleHints.get().put((String)invocation.getArgument(0), deadline.toEpochMilli());
        if (optional.orElse(null) instanceof io.modelsync.core.WorkLease lease) {
          values.put("lease_token", lease.token()); values.put("generation", lease.generation());
        }
      }
      if (role.equals("coordinator") && method.equals("finish")) values.put("transaction_returned", true);
      if (role.equals("reservation") && method.equals("schedule")) {
        values.put("eligible_bindings", String.valueOf(ReflectionTestUtils.getField(result, "waitingBindings")));
        values.put("hints", Map.copyOf(scheduleHints.get()));
        @SuppressWarnings("unchecked") var owned=(Map<String,?>)ReflectionTestUtils.getField(result,"reservations");
        var after=slots(bean);var reasons=new LinkedHashMap<String,String>();
        long now=((Number)values.get("schedule_clock_ms")).longValue();
        scheduleHints.get().forEach((group,deadline)-> {
          @SuppressWarnings("unchecked") var state=(Map<String,Object>)after.get(group);
          reasons.put(group, now<deadline?"FUTURE_ADMISSION_HINT":owned.containsKey(group)?"LOCALLY_RESERVED"
              :Boolean.TRUE.equals(state.get("occupied"))?"LOCAL_RESERVATION_OCCUPIED":"RESERVATION_RACE_UNATTRIBUTED");
        });
        values.put("reasons",reasons);
      }
      if (role.equals("store") && method.equals("claim") && result instanceof Optional<?> found)
        values.put("reason",found.isPresent()?"CLAIMED":invocation.getArguments().length==4 && ((Set<?>)invocation.getArgument(3)).isEmpty()
            ?"WAITING_FILTERED_RECOVERY_SELECTOR_EMPTY":"ELIGIBLE_BINDING_SELECTOR_EMPTY");
      if (result instanceof io.modelsync.core.QuotaAdmission admission) {
        values.put("granted", admission.granted());
        if (admission.retryAt() != null) values.put("retry_ms", admission.retryAt().toEpochMilli());
      }
      return result;
    } catch (Throwable failure) {
      values.put("failure_type", failure.getClass().getName());
      Throwable cause = failure;
      for (int depth=0; cause != null && depth<16; depth++, cause = cause.getCause()) if (cause instanceof java.sql.SQLException sql) {
        values.put("sql_state", String.valueOf(sql.getSQLState())); values.put("vendor_code", sql.getErrorCode()); break;
      }
      throw failure;
    } finally {
      if (role.equals("reservation")) values.put("after", slots(bean));
      values.put("duration_ns", System.nanoTime() - began); event(role + "-" + method, values);
      if (scheduling) { if(previousHints==null)scheduleHints.remove();else scheduleHints.set(previousHints); }
    }
  }
  @SuppressWarnings("unchecked") private Map<String, Object> slots(Object scheduling) {
    var result = new LinkedHashMap<String, Object>();
    var slots = (Map<String, AtomicReference<?>>) ReflectionTestUtils.getField(scheduling, "slots");
    slots.forEach((group, slot) -> {
      Object reservation = slot.get();
      result.put(group, reservation == null ? Map.of("occupied", false) : Map.of("occupied", true,
          "token", String.valueOf(ReflectionTestUtils.getField(reservation, "token")),
          "until_ms", ((Instant)ReflectionTestUtils.getField(reservation, "until")).toEpochMilli()));
    }); return result;
  }
  public void close() { if (enabled) { Hooks.resetOnEachOperator(hook); Schedulers.resetOnScheduleHook(hook); } lane.remove(); }
}
