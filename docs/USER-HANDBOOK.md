# Model Sync user handbook

**Edition:** job, event and age-on-visit extension — 2026-10-20.
**Audience:** Spring application developers and integration operators.
**Before production:** read [validation and review scope](REVIEW.md), [production runbook](PRODUCTION.md) and [arc42 architecture](ARC42.md).

## 1. Choose the behavior you need

There are three independent settings, not three separate engines:

| Question | Answer in the API |
|---|---|
| What requests work? | An explicit `ModelSync.request`, a `SyncRequested` event, or `SyncAccess.visit` |
| Who processes it? | The continuous worker, a bounded `SyncJob`, or your headless invocation |
| Should it repeat without another request? | `RefreshMode.PERIODIC` or `RefreshMode.ON_DEMAND` on the binding |

For a cache used only when a customer opens a page, choose **ON_DEMAND + visits**, optionally also source-change events, with an executor running elsewhere. For a replica that must refresh even when nobody visits, keep **PERIODIC**. Initial seeding/discovery is your responsibility in both cases.

Events and visits enqueue durable work; they never call the owner inline. With both executor options disabled and no external runner, work stays queued. Job execution does not enumerate an external source or replace Spring Batch inventory orchestration.

## 2. Build and install

Use Java 21 and Maven with access to your approved artifact mirror. The existing POM pins Spring Boot BOM `4.1.1`; this update does not change it or claim a successful full dependency build.

```bash
bash scripts/test-triggers.sh
python3 scripts/check-architecture.py
python3 scripts/check-docs.py
java scripts/SourceSyntaxCheck.java .
mvn -B -ntp verify
mvn -B -ntp install
```

The first four commands are the offline/static lane. Maven runs the framework/H2 test lane and packages artifacts. This update's local evidence is described in [REVIEW.md](REVIEW.md); supplied integration tests must not be mistaken for executed ones.

Application dependency:

```xml
<dependency>
  <groupId>io.modelsync</groupId>
  <artifactId>model-sync-spring-boot-starter-oracle-jpa</artifactId>
  <version>0.2.0-SNAPSHOT</version>
</dependency>
```

Add `model-sync-spring-boot-starter-webclient` at the same version for native asynchronous owner HTTP. Neither client starter chooses a web-server stack for you. Publish approved binaries to an internal repository; these coordinates are not a claim of publication to Maven Central.

## 3. Prepare storage and transaction configuration

For a **fresh dedicated schema**, provision the appropriate script under:

- [Oracle v2](../model-sync-persistence-jdbc/src/main/resources/model-sync/schema/oracle-v2.sql)
- [H2 v2, tests only](../model-sync-persistence-jdbc/src/main/resources/model-sync/schema/h2-v2.sql)

Do not execute fresh-install DDL against an existing installation. Existing v1 installations can remain periodic; ON_DEMAND requires the reviewed [v1-to-v2 migration procedure](PRODUCTION.md). Runtime startup rejects an on-demand binding if its work store cannot support idle completion.

Use one datasource and a local transaction manager that enlists both the JPA projection and JDBC ledger. With JPA, configure the corresponding `JpaTransactionManager`; do not create an unrelated JDBC-only transaction around JPA changes. Disable open-session-in-view and use `ddl-auto: validate` in production. The starter validates datasource alignment for its standard configuration, but your real integration test must prove rollback across both stores.

The sample's default `demo` profile creates an in-memory H2 v1 ledger and a synthetic customer owner. It remains a one-shot periodic demonstration and exits. It deliberately does not exercise v2, real Oracle or the new scheduler. Never apply its `create-drop` configuration to a real database.

### 3.1 Optional Oracle point-lock policy

The compatible default remains ordinary point `FOR UPDATE`. To explicitly opt in
for an Oracle datasource and a qualified JPA mapping:

```yaml
model-sync:
  jdbc:
    point-lock-policy: ORACLE_NOWAIT
  jpa:
    lock-policy: ORACLE_NOWAIT
```

The JDBC property configures the default `JdbcWorkStore` bean; a replacement bean
uses its own explicit constructor policy. The JPA property supplies a replaceable
`JpaLockPolicy` bean. Your application-owned projection binding must consume it:

```java
@Bean
JpaProjectionStore<Customer, CustomerEntity> customerProjection(
        EntityManagerFactory emf, JpaLockPolicy lockPolicy) {
    return new JpaProjectionStore<>(
        SharedEntityManagerCreator.createSharedEntityManager(emf),
        new CustomerJpaMapping(), lockPolicy);
}
```

The sample mapping implements `LockingJpaProjectionMapping`. An opted-in custom
mapping must explicitly implement that interface and honor the policy on every
pessimistic lookup, including tenant scoping; pass `policy.properties()` to its
per-operation JPA lookup. The store passes those properties separately to refresh.
It rejects legacy mappings when opted in, rather than silently ignoring the policy.
Qualify actual generated SQL with your database/provider; implementing an interface
or setting a hint is not runtime proof. The old two-argument store constructor
retains DEFAULT even if a policy bean exists. No global EntityManager, datasource
or application transaction-manager setting is changed.

Oracle54 refusal on an opted-in adapter becomes `LocalContention`. Adapters
copy only Oracle code/state into a detached diagnostic cause for this outcome;
the contention exception carries no ORM entity, session or JDBC connection.
The transaction
must roll back before `coordinator.failed` persists a separate LOCAL_CONTENTION
retry; calling failure persistence inside an active transaction is rejected.
This is a failed local work attempt, not an owner/network failure. Existing
retry-base/retry-max, exponential 0.5-1 jitter and max-attempts apply; DEAD is not
revived by passive visits. No in-place retry loop is added. Provider cooldown and
already spent admission are retained, including when a later local commit fails.

If the single retry-metadata write also refuses, its exception propagates with
the original failure retained as suppressed evidence. The committed lease remains
recoverable; no recursive metadata retry occurs. The attempt counter cannot advance
when its write cannot commit, so max-attempts is not a finite global bound across
permanent metadata-write failures. Recovery is lease-paced and may repeat an owner
read with another admission. Monitor this condition; it is not successful drain.
A failed enqueue must not be acknowledged as accepted, and an ID returned inside
a larger business transaction remains provisional until that transaction commits.
`runOne()` returning true means an attempt ran, not that synchronization completed.

**This is not complete bounded persistence.** Missing work/projection/state inserts,
quota registration, extra flush targets and custom SQL are unchanged and
can still wait. Known external IDs do not remove local first-insert conflicts.
Oracle26 native INSERT/UPDATE NOWAIT remains a separate, unshipped capability;
the test-only rewriting interceptor is not a consumer feature. Configure and
qualify connection/acquisition, transport, transaction and shutdown controls
separately. See the [coverage matrix](quality/LOCK-POLICY-COVERAGE.md),
[accepted path-scoped decision](quality/LOCK-POLICY-DECISION.md) and
[current QM](../QM.md). The original cancellation failure/gate remains unchanged.

Measured review source `2eea1d849dfd8d5f7f0ee1ee6f3386412b282477` passed
default141 JUnit/72 Python and focused Oracle21/21: seven integration cases plus
the same seven atomicity assertions in DEFAULT and ORACLE_NOWAIT. Run
`d7f2d3fe-348c-41a5-89b3-0f7ed0785200` retains actual SQL, exact sessions,
rollback/recovery and cleanup. This is existing-row/single-mapping evidence,
not complete persistence containment or full release qualification. The active
existing-row criterion was subsequently accepted as recorded in the decision;
the unprotected plain-UPDATE requirement remains active. Production remains NO-GO.

### 3.2 Shared quota existing-row lock policy

`model-sync.jdbc.quota-lock-policy=ORACLE_NOWAIT` independently opts the default
JdbcQuotaGate bean into Oracle NOWAIT for its existing-row admission and cooldown
SELECT locks. The compatible default and registration INSERT remain unchanged.
Replacement gates choose their own constructor policy. This is not an instruction
to append NOWAIT to every DML statement or to change datasource/global timeouts.

Admission is returned only after its independent transaction commits. Lock refusal
rolls back before LocalContention reaches the caller; no owner invocation is allowed
on that exception. The coordinator persists a separate bounded LOCAL_CONTENTION
retry when the work row is writable. Cooldown returns normally only after its
transaction commits, uses max(previous,requested), and never reports an uncommitted
extension as persisted. A refused cooldown propagates once with the original owner
failure retained; it does not recursively retry or falsely persist DEFERRED metadata.
The durable lease remains recoverable; its attempt count cannot advance without a
metadata commit. Recovery may repeat a read and spend a new admission. A failed
cooldown is an operational failure requiring monitoring, not distributed assurance
that all other instances know the requested extension. Existing committed cooldown
and spent admission are retained. There is no global finite-attempt promise across
permanently unavailable persistence.

The owner accepted the earlier existing-work/JPA subset in the
[scoped decision](quality/LOCK-POLICY-DECISION.md). First inserts, quota, additional
flush targets and transport/uncertain COMMIT were not waived. New quota measurements
are reported separately in QM; implementation alone is not qualification.
See [supported writers](quality/SUPPORTED-WRITERS.md): concurrent compliant instances
remain in scope, arbitrary external starter-table writers/triggers do not. Legitimate
first work/projection/state/quota insert paths remain in the coverage matrix.

Clean source4057535/tree4bfbfae passed default149 JUnit/76 Python and six focused
Oracle quota tests (run35ead9f5-f6af-4c49-a323-4ad149737b85). Two independent starter
applications observed eight held-blocker refusals, rollback and healthy pools;
three synthetic owner reads produced two completed reconciliations after recovery.
One case injects RateLimitedAfter after a successful synthetic read; it is not an
HTTP429 transport test. Concurrent first registration stays unqualified. Full/PIT/
load were not run; see [current QM](../QM.md) for exact commands, source and raw data.

## 4. Define the model, owner and local mapping

For the two demonstrated first-work/quota races, the independent explicit option
`model-sync.jdbc.insert-policy=ORACLE_26_NOWAIT` selects native single-row INSERT
NOWAIT only in JdbcWorkStore enqueue/enqueueRefresh and JdbcQuotaGate.register.
It requires Oracle26ai metadata (JDBC database version23.26); older/unrecognized
lines reject this opt-in. DEFAULT/old constructors retain ordinary INSERT and do
not change the supported default database/platform contract. Replacement beans
choose their own constructor capability. No JPA mapping SQL is rewritten, and no
state INSERT, ordinary UPDATE, queue or existing-row lock policy is changed.

A conflict throws detached LocalContention, never a ticket/configuration acceptance.
Public REQUIRED enqueue rolls back the whole attempt (and joins/marks an outer
transaction as appropriate); registration's independent transaction rolls back
before reporting refusal. A caller must retry only from a new transaction using
its bounded admission/redelivery policy. Startup registration refusal fails startup;
the library does not create a restart or immediate retry loop. Existing compatible
duplicate-key handling remains for committed races. Native statement rollback is
not itself whole-transaction rollback. A ticket inside a caller transaction remains
provisional until commit. Oracle transport/uncertain COMMIT are separate controls.
See [reachability and measured protocol](quality/FIRST-CREATION-REACHABILITY.md).
The new candidate still needs its own recorded Oracle run; pre-control evidence
does not qualify native INSERT. Failed cooldown publication remains unchanged.

The minimal application integration has three responsibilities: read the owner, map a local projection, and declare their binding.

A domain DTO contains the owner-controlled fields:

```java
public record Customer(String displayName, String status) {}
```

The owner is a `BlockingOwner<Customer>` or `AsyncOwner<Customer>`. It returns `FetchResult` outcomes and translates failures into the library's retry/permanent categories. The actual source identifier, authentication and endpoint contract belong to your adapter.

The local `ProjectionStore<Customer>` can be a `JpaProjectionStore<Customer, CustomerEntity>` using an application `JpaProjectionMapping`. See the complete existing [customer mapping](../model-sync-sample/src/main/java/io/modelsync/sample/CustomerJpaMapping.java) and [entity](../model-sync-sample/src/main/java/io/modelsync/sample/CustomerEntity.java). The mapping must enforce tenant isolation, carry a revision, and update only synchronized fields. The sample deliberately leaves `reviewRequestedAt` locally owned.

In an application configuration class, with imports from `io.modelsync.core`, `io.modelsync.spring`, `java.time` and Spring's `@Bean`:

```java
@Bean
ModelBinding<Customer> customerBinding(
        @DataOwner("customer") BlockingOwner<Customer> owner,
        @DataCache("customer") ProjectionStore<Customer> cache) {
    return ModelBinding.builder("customer", Customer.class)
        .boundary("cards")
        .owner("external-customer-system", owner)
        .cache(cache)
        .fingerprint(c -> Digests.sha256Fields(
            "customer-map-v1", c.displayName(), c.status()))
        .quota("customer-owner")
        .freshness(new FreshnessPolicy(
            Duration.ofHours(1),  // validation becomes due
            Duration.ofDays(1),   // hard serving age limit
            Duration.ofDays(2))) // local projection expiry horizon
        .refreshMode(RefreshMode.ON_DEMAND)
        .repairFromOwner()
        .build();
}
```

Omit `.repairFromOwner()` for compare-only behavior. Omit `.refreshMode(...)` to retain the previous periodic default. Use `.replica(store)` instead of `.cache(store)` for a non-expiring replica; the serving-age policy still applies.

`@DataOwner`, `@DataReplica`, `@DataCache` and `@BoundaryOwner` are bean qualifiers, not entity ownership enforcement or method interception. Explicit qualifiers/beans are equally valid. The declared binding is the runtime route.

## 5. Run continuously or on a job schedule

### 5.1 Continuous worker

An example with an illustrative quota (adapt it to the provider's actual contract):

```yaml
model-sync:
  enabled: true
  worker-enabled: true
  quotas:
    customer-owner: 750ms
  lease: 60s
  fetch-timeout: 15s
  poll-interval: 250ms
  productive-interval: 5ms
  max-idle-interval: 1s
  shutdown-timeout: 5s
  concurrency: 4
  persistence-threads: 4
  owner-threads: 8
  queued-tasks-per-thread: 64
  triggers:
    events-enabled: true
    job:
      enabled: false
spring:
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
```

The auto-configured continuous worker has four independent lanes by default. After a completed
owner fetch/application attempt, a lane waits `productive-interval` (positive, default 5 ms).
Empty claims, prepare-only/deferred work and failures wait 250 ms, then 500 ms, then at most
`max-idle-interval` (default 1 s). A fetched result resets that lane's idle backoff. This does
not change persisted retry/cooldown, distributed provider admission or lease fencing. A lane
does not cache queue deadlines: it rechecks at least once per maximum idle interval, subject
to database/scheduler availability. New work from another process needs no local signal.
Worker stop first closes new claim admission and waits up to `shutdown-timeout` (positive,
at most five minutes). `shutdown()` distinguishes DRAINED from TIMED_OUT and records both
logical and tracked physical operations. Remaining subscriptions are cancelled at the bound;
this does not prove that JDBC/owner transports stopped. `physicalOperations()` observes actual
blocking call exits and uncancelled async-stage completions; the owner must define what its
stage completion means for HTTP resource release. A late cancelled result cannot apply.
Owner capacity is retained until that physical callable/stage completes. Restart is refused
while previous operations remain unresolved. `stop(Runnable)` acknowledges bounded stop,
including timeout, rather than promising successful drain. Do not call blocking `stop()` from
a Reactor event loop; use the host's lifecycle/control thread.
Configure datasource, statement/lock, transaction and owner transport bounds in the host;
the starter does not globally change application-owned resources. See the
[failure contract](quality/FAILURE-CONTRACT.md). `activity()` reports scheduling counters,
not successful syncs or proof of physical resource cleanup.
Set both extra intervals to `poll-interval` for the former fixed delay after every tick.
The existing five-argument `ReactorSyncWorker` constructor retains that fixed-delay behavior.

`750ms` is a shared minimum admission spacing, roughly 80 admissions per minute under ideal continuous execution. It is not a burst/rolling-window quota model or a guaranteed provider arrival rate. Reserve capacity for other applications and retries.

### 5.2 Bounded cron job

Replace the executor flags with:

```yaml
model-sync:
  enabled: true
  worker-enabled: false
  quotas:
    customer-owner: 750ms
  triggers:
    events-enabled: true
    job:
      enabled: true
      cron: "0 */5 * * * *"
      zone: "Europe/Berlin"
      max-items: 100
      max-duration: 30s
```

The expression has six Spring cron fields, including seconds. The scheduler owns its thread; you do not need global `@EnableScheduling`. Choose UTC to avoid local daylight-saving ambiguity when wall-clock business timing is irrelevant. These numbers illustrate configuration, not a recommended production capacity.

Each run attempts at most 100 claimed items or stops after its between-attempt time budget. `attempted` includes work deferred by quota or failure and is not the number of successful synchronizations. The job is sequential. Its 30-second budget cannot interrupt a blocking client that ignores transport timeouts. The backlog remains durable for later runs; missed cron firings are not stored as individual job executions.

Enabling both the built-in continuous worker and built-in cron runner in one application fails startup. Several worker deployments can share the ledger, but quota and database contention must be tested. To have a single cron firing across pods, use your deployment/scheduler policy; the library guarantees subject claiming, not a cluster-wide cron singleton.

### 5.3 External scheduler or manual command

Inject `SyncJob`, keep both built-in executors disabled, and invoke outside any caller database transaction:

```java
SyncJobResult result = job.run(500, Duration.ofMinutes(1));
// Inspect result.attempted() and result.stopReason().
```

A Kubernetes CronJob, enterprise scheduler or application command can call this API. The project does not provide a production-ready Kubernetes manifest or Spring Batch `JobRepository` integration.

## 6. Event-triggered synchronization

### 6.1 Direct, framework-neutral event adapter

```java
String ticket = triggers.accept(
    SyncRequested.changed("customer", tenant, sourceId));
```

`changed(...)` requests HIGH priority and forced validation. The worker therefore does not skip the fetch solely because the previous validation was recent. The event contains identity, not a trusted replacement payload.

For a non-change hint, construct the record with `force=false`:

```java
var hint = new SyncRequested(
    "customer", new SubjectKey(tenant, sourceId), Priority.NORMAL, false);
triggers.accept(hint);
```

The returned ticket is stable for the subject. It is not an event ID or immutable audit record. A returned ticket inside an outer transaction is not durable until that outer transaction commits.

### 6.2 Spring application events

Enable `model-sync.triggers.events-enabled=true`, then:

```java
publisher.publishEvent(SyncRequested.changed("customer", tenant, sourceId));
```

The listener enqueues synchronously and opts out of asynchronous multicasting. When combining a local business mutation with the event, publish **before** the mutation inside the same local transaction to preserve ledger-first lock ordering:

```java
localTransactions.required(() -> {
    publisher.publishEvent(SyncRequested.changed("customer", tenant, sourceId));
    updateLocallyOwnedFields(tenant, sourceId); // application method
    return null;
});
```

Do not catch and suppress enqueue errors. Do not replace this listener with a plain after-commit enqueue callback: a crash between business commit and enqueue can lose the request. A read-only caller transaction is rejected. The listener does not invoke the owner or wait for synchronization.

For a durable remote event stream, validate/authorize the message and commit the Oracle intent before acknowledging the broker. An inbox table keyed by the broker event ID may be required to suppress replay after previous work has completed. No Kafka/RabbitMQ consumer, inbox or exactly-once protocol is supplied by this extension.

A change event is **not instant cache invalidation**. Existing trusted snapshots can still be returned according to their age until reconciliation changes the evidence. Critical operations requiring the latest source version need an application version barrier or an explicitly designed owner-read workflow.

## 7. Age/retention-on-visit synchronization

Expose a typed access bean:

```java
@Bean
SyncAccess<Customer> customerAccess(SyncTriggers triggers) {
    return triggers.model("customer", Customer.class);
}
```

Then call it from an authorized service boundary:

```java
VisitResult<Customer> result = customerAccess.visit(
    tenant, sourceId, VisitPolicy.STALE_WHILE_REVALIDATE);
```

A visit does local I/O and may enqueue. It never calls the external API, waits for a quota permit or blocks until a refresh completes.

| Result | Meaning | Suggested application response |
|---|---|---|
| Projection, FRESH, no ticket | Trusted snapshot below refresh age | Serve the value |
| Projection, STALE, ticket | Bounded stale snapshot and refresh requested | Serve only where bounded staleness is acceptable; expose freshness metadata |
| No projection, ticket | Missing/expired/unverified, or strict freshness required | Pending response, typically an application-defined 202 with retry/status metadata |
| No projection and `refreshSuppressed()` | Refresh needed but subject quarantined | Unavailable/error handling and operator investigation; not automatic 404 |
| STALE projection, no ticket and `refreshSuppressed()` | Bounded stale value is allowed but refresh is quarantined | Consider serving with degraded status; alert and stop at age limit |

Use `VisitPolicy.REQUIRE_FRESH` to refuse stale values. This still returns pending; it does not fetch synchronously. Never treat an empty projection as authoritative non-existence.

### The three time limits

`refreshAfter` answers “when should we validate again?” `maxAge` answers “how old may evidence be before serving is rejected?” `retention` controls the projection's expiry when the cache is written/touched after successful validation.

Equality reaches the boundary: age exactly `refreshAfter` is stale; age exactly `maxAge` is expired. A projection's own expiry can reject it sooner. Visiting never renews either timer. There is no physical deletion job or sliding last-access TTL in this version.

A hot key does not produce a new work generation on every visit. Existing leases are reused; retry/cooldown timestamps remain intact. Passive visits do not revive `DEAD` work. An authorized explicit `ModelSync.request(..., true)` can revive it after investigation.

Use ON_DEMAND to avoid automatic polling after completion. Use PERIODIC to maintain freshness even for unvisited subjects. Both policies retain evidence and retry failures. Changing an existing IDLE binding to PERIODIC does not scan/awaken all idle rows automatically; explicitly re-enqueue a bounded subject population.

## 8. WebFlux usage

JPA remains blocking. Place local database work on the named persistence scheduler, not a Netty event-loop thread:

```java
@Bean
ReactiveSyncAccess<Customer> reactiveCustomerAccess(
        SyncAccess<Customer> access,
        @Qualifier("modelSyncPersistenceScheduler") Scheduler persistence) {
    return new ReactiveSyncAccess<>(access, persistence);
}
```

With imports from `io.modelsync.reactor`, `reactor.core.scheduler` and Spring qualifiers:

```java
Mono<VisitResult<Customer>> result = reactiveCustomerAccess.visit(
    tenant, sourceId, VisitPolicy.REQUIRE_FRESH);
```

The `Mono` is lazy. Each subscription executes the visit again, although passive admission coalesces its work. Compose it into the web request pipeline rather than calling `.subscribe()` internally. Do not use `.block()` on an event loop.

For an asynchronous broker integration:

```java
Mono<String> accepted = ReactiveSyncAccess.accept(
    triggers, SyncRequested.changed("customer", tenant, sourceId), persistence);
```

Acknowledge only after successful completion and the relevant local commit. Reactive context is not an automatic propagation of a thread-bound JPA transaction. When a business mutation must be atomic with enqueue, put the entire local transaction on the same persistence execution boundary. The narrow `@RequestSync` annotation intentionally does not support reactive return types.

## 9. Optional AOP and explicit alternatives

For synchronous application commands, `@RequestSync` can enqueue before a method mutation:

```java
@RequestSync(binding = "customer", tenant = "#p0", key = "#p1",
             priority = Priority.HIGH, force = true)
public void markForReview(String tenant, String sourceId) {
    // Update only locally owned fields here.
}
```

Enable `model-sync.aop.enabled=true`. The method must be public, non-final and invoked through a Spring proxy. Do not combine this annotation on the same method/class with `@Transactional`, Jakarta `@Transactional` or `@Async`. `#result` is not supported: identity is required before business mutation. Self-calls bypass the advice. See [the detailed AOP design](AOP.md).

For complex transactions, use `LocalTransactions.required`, enqueue first and mutate second. Events, explicit calls and AOP are alternative entry mechanisms, not layers to stack on one operation. Combining all of them can cause needless extra generations.

## 10. Inspect and troubleshoot

```java
ModelSync<Customer> customers = gateway.model("customer", Customer.class);
SyncState state = customers.status(tenant, sourceId);
Optional<Projection<Customer>> safe = customers.local(tenant, sourceId);
Optional<Projection<Customer>> fresh = customers.freshLocal(tenant, sourceId);
```

`status()` reports last reconciliation evidence, not live queue execution state. `local()` and `freshLocal()` are read-only views; unlike `visit()`, they never enqueue. `inspect()` is a diagnostic view and may contain unsafe-to-serve data. A ticket does not provide a built-in HTTP status endpoint; implement and authorize that endpoint in your application.

| Symptom | Check first |
|---|---|
| Ticket exists but no owner calls | Executor enabled somewhere? Item due? Quota cooldown? Fresh non-forced shortcut? |
| `ON_DEMAND requires ... schema v2` | Migrate safely or keep the binding periodic; do not edit schema metadata alone |
| Repeated visit produces no ticket | Subject may be DEAD; inspect ledger/error and investigate before replay |
| Data exists but visit returns pending | Digest match, last outcome, maximum age, local expiry or strict visit policy |
| MISMATCH persists | Verify-only binding? Incorrect mapping/digest? Unauthorized repair must stay disabled |
| Owner call succeeds but no projection change | Absence, local revision conflict, lost lease, compare-only mode or identical data |
| Too many requests after messages | Broker redelivery after completion, forced explicit requests, missing inbox, hidden client retries |
| High due lag despite more pods | Provider budget, database contention, cron window or insufficient executor capacity |
| WebFlux request blocks an event loop | Use `ReactiveSyncAccess`, check custom adapter/scheduler boundaries |

The [runbook](PRODUCTION.md) gives SQL diagnostics and recovery discipline. Do not clear lease tokens or all failures indiscriminately.

## 11. Reference: new configuration and compatibility

| Property / API | Default | Effect |
|---|---|---|
| `model-sync.enabled` | false | Existing runtime gate |
| `model-sync.worker-enabled` | false | Existing continuous Reactor executor |
| `model-sync.triggers.events-enabled` | false | Register synchronous application-event listener |
| `model-sync.triggers.job.enabled` | false | Register lifecycle-managed cron executor |
| `model-sync.triggers.job.cron` | `0 */5 * * * *` | Six-field cron schedule |
| `model-sync.triggers.job.zone` | `UTC` | Explicit cron timezone |
| `model-sync.triggers.job.max-items` | 100 | Maximum claimed attempts in one run |
| `model-sync.triggers.job.max-duration` | 30 seconds | Budget checked between attempts |
| `ModelBindingBuilder.refreshMode(...)` | PERIODIC | Recurring background due time or on-demand IDLE |
| `SyncAccess.visit(..., policy)` priority | HIGH | Passive request promotion; not force or retry bypass |
| `SyncRequested.changed(...)` | HIGH, force=true | Source-change hint requires owner validation |

Freshness remains a Java binding policy, not an undocumented YAML field. Do not add `model-sync.retention-on-visit` or a per-binding YAML map and expect it to work: neither is implemented.

Custom `WorkStore` implementations must implement `enqueueRefresh` to support visits safely. The default fails closed rather than using unsafe general enqueue semantics. On-demand additionally requires `supportsOnDemand()` and `completeOnDemand(...)`. The existing periodic methods remain supported. Custom conditional beans can replace defaults, but must preserve the same transaction and admission contracts.

## 12. IBM MDM, boundaries and Vista/DWH

Use `IdentityResolver` to integrate MDM identifiers, aliases and ambiguity. Do not assume the source expects an MDM ID merely because the local projection stores one. Persist the source namespace and tenant; map to the current source identity under an application policy.

`gateway.boundaryModels("cards")` answers “which bindings are configured here?” It does not fetch all remote entities. A remote inventory/delta importer must checkpoint its page and intents atomically and define snapshot consistency. This importer is not supplied.

Vista/DWH dataset ports support a separate analytical integration. Compare manifests only when grain, snapshot/cutoff, filters and schema/mapping versions agree. Distinct MDM ID count alone is not a universal duplicate rule, especially for transaction-grain facts. Track missing/unresolved coverage; excluding failed subjects and reconciling only the rest would hide incompleteness.

No product-specific IBM MDM/Vista client or durable downstream publication mechanism is introduced by the trigger extension. Do not use an analytical mismatch to write back to the operational owner automatically.
