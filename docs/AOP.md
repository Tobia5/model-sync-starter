# AOP design: narrow, optional and transaction-aware

## 1. Why annotations do not define authority

A domain class can be a source-owned DTO in one route and a cached projection in another. Annotating the entity as globally “owner” or “slave” mixes authority with storage. This library instead uses a class-based `ModelBinding<T>` as the route definition.

The optional role annotations are Spring custom qualifiers (official reference S8). They help distinguish beans at injection points:

```java
@Bean
@DataOwner("customer")
BlockingOwner<Customer> customerOwner(/* approved HTTP client */) {
    // Return an application adapter or the supplied RestClientOwner.
}
```

This abbreviated example describes the injection shape; the complete implementation is `RestOwnerConfiguration` in the sample. The qualifier does not create an HTTP client, invent an endpoint, grant write permissions, or trigger synchronization. The builder must still reference the selected bean.

A `@BoundaryOwner("cards") BoundaryInventory` bean similarly identifies a catalog capability. It does not cause all remote types or all local entities to become synchronized automatically.

## 2. The only behavioral annotation: @RequestSync

`@RequestSync` is placed on a public, non-final, synchronous service method whose identity is known from its input. Its metadata is the binding ID, tenant expression, key expression, priority and force flag.

```java
@RequestSync(binding = "customer", tenant = "#p0", key = "#p1",
             priority = Priority.HIGH, force = true)
public void requestReview(String tenant, String id) {
    // Modify a locally owned review flag; do not write owner-owned fields.
}
```

Its semantics are **commit a durable request with the local mutation**, not **perform synchronization before returning**. The return value remains the business method's result. The annotation does not wait for the external owner.

## 3. Exact advice sequence

```text
proxy invocation
  resolve concrete annotated method
  validate synchronous/public/non-final contract
  validate known model binding
  reject read-only caller transaction
  begin or join REQUIRED local transaction
    evaluate input expressions
    enqueue/coalesce work; acquire the work row lock
    invoke business method
  commit (both durable intent and business mutation)
return original business result
```

If expression evaluation, enqueue, business code, flush or commit fails, the transaction fails. Checked business exceptions are temporarily wrapped so the transaction rolls back and then rethrown as the original exception. This does not depend on the default rollback convention of an unrelated `@Transactional` annotation.

The worker cannot see uncommitted work. The annotation writes the intent before the business rows so its lock order agrees with the worker: **ledger, then projection**. This is why `#result` identity expressions are rejected in version 0.1. Preallocate identifiers for creates or use an explicit, reviewed ingestion/outbox design when identity only exists after the call. Do not silently switch to after-commit in-memory events.

The advice is not an after-commit listener. An after-commit wake-up could be added as a latency optimization, but Oracle polling and the committed intent remain the recovery mechanism. Spring Modulith's publication registry is another relevant durable-publication design; it is not a dependency or an implementation hidden in this starter (S9).

## 4. Pointcut and expression design

`RequestSyncAdvisor` extends `AbstractPointcutAdvisor` and uses a `StaticMethodMatcherPointcut` matching only explicit `@RequestSync` methods. It is not a global repository or RestClient pointcut. It does not intercept every JPA write or HTTP call.

Input expressions support `#p0`, `#p1`, etc., and their `#a0` aliases; read-only property navigation is supported through `SimpleEvaluationContext`. String literals such as `'default-tenant'` are possible in application-owned annotation metadata. `#result` is explicitly rejected. Expression strings are never accepted from an HTTP request or external provider response.

The advisor validates before business execution. A `BeanPostProcessor` validates annotated signatures at startup so invalid deferred methods are not silently ignored. An expression resolving to null is an error, not a missing-ID request.

## 5. Transaction ownership and exclusions

The annotation supplies REQUIRED semantics through `SpringLocalTransactions`. Therefore this version rejects a method or class simultaneously annotated with Spring/Jakarta `@Transactional`. It also rejects `@Async`, `Future`, `CompletionStage`, and reactive return types. There is no ambiguous choice between two competing transaction advisors on the same boundary.

An outer transaction on a different service may be joined. Such a caller must use the same datasource/transaction manager and maintain ledger-before-projection lock ordering. Calling into the annotated service after locking the same projection from an outer transaction may deadlock with a worker. A read-only outer transaction is rejected. Multiple subject locks in a single transaction should be acquired in deterministic work-key order.

A local database transaction cannot roll back external HTTP, messages sent without an outbox, emails, or other irreversible side effects. Do not place those effects in the annotated method and assume this advisor makes them atomic.

## 6. Proxy limitations are deliberately visible

Spring AOP is proxy-based (S4). Self-invocation, such as `this.requestReview(...)`, does not pass through the proxy and will not execute this advice. Calls on objects created with `new`, rather than the managed Spring bean, also bypass it. Final/private methods cannot serve as these interception boundaries. The included tests demonstrate self-invocation behavior rather than pretending to solve it.

For predictable matching, put the annotation on the public concrete service method. The starter is not an AspectJ compile-time or load-time weaving framework. `aspectjweaver` supports Spring's proxy infrastructure here; its presence is not a claim of woven entity interception.

## 7. Programmatic equivalent

No annotation is required. The explicit operation makes transaction and lock ordering visible:

```java
localTransactions.required(() -> {
    customers.request(tenant, id, Priority.HIGH, true); // ledger first
    updateLocallyOwnedReviewFlag(tenant, id);           // application JPA code
    return null;
});
```

This fragment belongs in an application service; `customers` is the injected `ModelSync<Customer>`. Both calls must use the same local transaction infrastructure. Calling `request()` after committing the business transaction loses the atomicity property even though each individual call succeeds.

The programmatic API is preferable for multi-step operations, generated-identity workflows and code where proxy behavior would be surprising. It is also easier to unit test without Spring.

## 8. WebFlux integration

Do not place `@RequestSync` on a method that merely returns a `Mono`. The method can return before its publisher runs, so an ordinary thread-bound JPA transaction cannot enclose the deferred mutation.

For a single work request, use `ReactiveModelSync.request`, which defers the blocking local call onto the persistence scheduler. For an atomic business change plus request, bridge the **whole synchronous transaction**, not individual repository calls:

```java
Mono<Void> updateAndRequest(String tenant, String id) {
    return Mono.<Void>fromRunnable(() -> localTransactions.required(() -> {
        customers.request(tenant, id, Priority.HIGH, true);
        updateLocallyOwnedReviewFlag(tenant, id);
        return null;
    })).subscribeOn(modelSyncPersistenceScheduler);
}
```

The application must supply these injected collaborators and its update method. Do not call `subscribe()` inside the service; return the publisher to the caller. Do not pass a managed entity or `EntityManager` between Reactor threads. This is a blocking-JPA bridge consistent with Reactor's guidance, not a reactive database transaction (S5).

## 9. Why there are no Hibernate callbacks

This version intentionally does not use `@PostLoad`, `@PostUpdate` or an Envers listener as the request engine. Entity callbacks have lifecycle/transaction limitations, bulk SQL can bypass ORM events, and network calls inside them would extend database transactions. Hibernate revision history and remote reconciliation solve different problems (S10/S11).

An optional future capture module can record a durable intent from supported Hibernate changes. It must test flush repetition, rollback, bulk-write bypass, lock ordering and new-identifier behavior. It must never turn a getter or lazy association into a rate-limited remote call. Existing input-ID request AOP remains usable without it.
