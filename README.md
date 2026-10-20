# Model Sync — Spring Boot reconciliation starter

**Explicit model bindings, durable priority work, external authority, and bounded-staleness local projections.**

Version: `0.1.0-SNAPSHOT` · Java 21 · Spring Boot BOM `4.1.1` · Oracle-first ledger · JPA business projections · optional WebClient.

> **Review status:** this extension adds explicit job, event and age-on-visit APIs, plus optional on-demand recurrence. The offline lane passes **77 existing + 41 new contracts**. Full Maven/Spring/H2 and Oracle qualification remain unexecuted locally. Start with the [user handbook](docs/USER-HANDBOOK.md), [complete arc42 architecture](docs/ARC42.md), [production runbook](docs/PRODUCTION.md) and [review/evidence report](docs/REVIEW.md). This is not a production-certified release.

## Job, event and age-on-visit synchronization

```java
// Source-change event: durable request, not an inline external call.
String ticket = triggers.accept(SyncRequested.changed("customer", tenant, sourceId));

// Explicit visit: trusted local value or pending/suppressed metadata.
VisitResult<Customer> view = triggers.model("customer", Customer.class)
    .visit(tenant, sourceId, VisitPolicy.STALE_WHILE_REVALIDATE);

// Bounded job: drains due work; does not enumerate the external population.
SyncJob.Result run = job.run(100, Duration.ofSeconds(30));
```

Choose `.refreshMode(RefreshMode.ON_DEMAND)` on a binding to leave it IDLE after completion instead of polling unvisited records. PERIODIC remains the default. Schema v2 is required for JDBC on-demand mode. Events/visits still need an enabled worker or job to execute their work. Passive visits preserve cooldown/backoff and never revive DEAD work.

The original one-shot sample remains unchanged and uses the periodic v1 path. See the handbook for v2 provisioning and opt-in cron/event configuration.

## The API decision

Use a **`ModelBinding<T>`** to declare a route and an injectable **`ModelSync<T>`** to request or inspect synchronization. `SyncGateway` locates those facades. The class is a type token, not a global statement about data ownership.

```java
ModelSync<Customer> customers = gateway.model(Customer.class);
String ticket = customers.request("tenant-a", "customer-42", Priority.HIGH);
Optional<Projection<Customer>> available = customers.local("tenant-a", "customer-42");
```

A request returns a durable, coalesced **work ticket**, not a completed synchronization or a unique audit event. `local()` never performs HTTP. It returns only verified data below maximum age; `freshLocal()` also rejects stale-but-bounded data. `inspect()` is an explicitly diagnostic path that may expose expired or unverified local data.

| Concept | Meaning in this implementation |
|---|---|
| Data owner | Authoritative reader for the mapped fields; not automatically a writable endpoint |
| Data replica | Durable local projection; authority remains elsewhere |
| Data cache | Local projection with independent retention and freshness limits |
| Boundary owner | Optional model catalog / paginated inventory capability for a bounded context |

`@DataOwner`, `@DataReplica`, `@DataCache`, and `@BoundaryOwner` are optional Spring **qualifiers**. They do not generate network clients, scan JPA entities, or override the explicit route. The term `DataReplica` replaces `DataSlave`.

## Build and run the included sample

Prerequisites: JDK 21 and a Maven installation with access to Maven Central or your approved artifact mirror.

```bash
# Already executable without external Java dependencies:
bash scripts/test-triggers.sh
python3 scripts/check-architecture.py
java scripts/SourceSyntaxCheck.java .

# Required in a dependency-enabled environment; not run in the generation environment:
mvn -B -ntp verify
mvn -B -ntp install
java -jar model-sync-sample/target/model-sync-sample-0.1.0-SNAPSHOT.jar
```

Windows core check: `powershell -ExecutionPolicy Bypass -File scripts/test-core.ps1`.

The sample is a **one-shot, headless demo**, not a running REST server. Its default `demo` profile initializes an in-memory H2 ledger, uses a synthetic owner, creates the sample JPA table, synchronizes one customer, and demonstrates an atomic review-request mutation plus work intent. It then exits. Never use the demo profile on an external database.

The project is not published to Maven Central. `mvn install` installs the snapshot locally. Publish reviewed artifacts to your own repository before depending on them from other build agents. There is no bundled Maven binary or misleading wrapper that depends on an unavailable download.

## Add it to a Spring Boot application

```xml
<dependency>
  <groupId>io.modelsync</groupId>
  <artifactId>model-sync-spring-boot-starter-oracle-jpa</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The opinionated client starters include Spring Boot client/JSON dependencies; direct adapter-module users must supply appropriate client and JSON configuration.

For native nonblocking owner HTTP, add `model-sync-spring-boot-starter-webclient` at the same version. The base starter does not select an MVC or WebFlux server. A headless application with WebClient dependencies should explicitly set `spring.main.web-application-type=none`.

Enable infrastructure only after installing the reviewed ledger DDL:

```yaml
model-sync:
  enabled: true
  worker-enabled: true
  aop:
    enabled: false
  quotas:
    customer-owner: 750ms
  lease: 60s
  fetch-timeout: 15s
  poll-interval: 250ms
  concurrency: 4
  persistence-threads: 4
  owner-threads: 8
  queued-tasks-per-thread: 64
spring:
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
```

`750ms` is an illustrative **shared admission spacing**, approximately 80 admissions/minute, leaving margin below an illustrative 100/minute provider limit. It is not a universal recommendation or a replacement for your actual provider contract. All worker instances for that quota must use the same Oracle ledger. Provider/network arrival times may differ from local admission times.

### Register three things: owner, projection mapper/store, binding

The complete source example is in [`model-sync-sample`](model-sync-sample/src/main/java/io/modelsync/sample/).

```java
@Configuration(proxyBeanMethods = false)
class CustomerSyncConfiguration {
    @Bean
    @DataCache("customer")
    JpaProjectionStore<Customer, CustomerEntity> customerCache(EntityManagerFactory emf) {
        return new JpaProjectionStore<>(
            SharedEntityManagerCreator.createSharedEntityManager(emf),
            new CustomerJpaMapping());
    }

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
                Duration.ofHours(1), Duration.ofDays(1), Duration.ofDays(2)))
            .repairFromOwner()
            .build();
    }

    @Bean
    ModelSync<Customer> customerSync(SyncGateway gateway) {
        return gateway.model(Customer.class);
    }
}
```

Imports are shown in the actual sample sources. Register the owner as a qualified `BlockingOwner<Customer>` bean, using `RestClientOwner` or your own implementation. For an async owner use `AsyncOwner<Customer>` and `.asyncOwner(...)` instead. A `ProjectionStore<T>` is a **local transaction participant**, never an arbitrary remote sink.

**Omit `.repairFromOwner()` for verify-only mode.** With it enabled, the mapper may update only fields owned by this source. The sample deliberately preserves the application's locally owned `reviewRequestedAt` field. Ownership is not inferred by reflecting over all entity fields.

When the same DTO has two routes, specify the route:

```java
ModelSync<Customer> operational = gateway.model("customer", Customer.class);
List<ModelBinding<?>> types = gateway.boundaryModels("cards");
```

Class-only lookup rejects ambiguity. `boundaryModels` returns registered bindings, not a promise that every remote model or every record has been discovered.

## Optional AOP: request, do not secretly synchronize

```java
@RequestSync(binding = "customer", tenant = "#p0", key = "#p1",
             priority = Priority.HIGH, force = true)
public void requestReview(String tenant, String customerId) {
    // Local JPA change only. This advice supplies REQUIRED transaction semantics.
}
```

Enable with `model-sync.aop.enabled=true`. The advisor validates input identity, registers the work intent **before** the business mutation, and commits both in the same local transaction. The worker sees the intent only after commit. This ledger-first order matches the worker's commit lock order.

Do not combine the annotated method/class with `@Transactional` or `@Async`. Deferred/reactive return types, final methods, and `#result` identity expressions are rejected. A normal synchronous outer transaction may be joined, but the application must preserve ledger-before-projection lock ordering. Self-invocation still bypasses Spring proxies. See [AOP design](docs/AOP.md).

## What is implemented, and what is a seam

Implemented source includes typed bindings, qualifiers, narrow request AOP, age-aware reads, verify-only/local-repair decisions, work coalescing, generation tracking, leases/fencing, CAS writes, bounded retries, weighted priority selection, shared JDBC quota admission/cooldown, Oracle and H2 DDL, JPA mapping, REST/WebClient owner adapters, Reactor bridges/worker, conditional auto-configuration, dataset manifest comparison, and a parameterized JDBC manifest reader.

IBM MDM identity resolution, external boundary enumeration, Vista, and DWH have **explicit interfaces**. There is no fabricated vendor protocol. This version does not implement an IBM MDM connector, a Vista-specific HTTP client, a durable full-inventory/delta importer, remote push delivery/outbox, CDC/Envers capture, or bidirectional field merging. The gateway's typed-boundary catalog and dataset comparison do work without those extensions.

## Reading order

[Architecture](docs/ARCHITECTURE.md) · [AOP and transaction design](docs/AOP.md) · [Integration examples](docs/INTEGRATION.md) · [Oracle operations and limitations](docs/OPERATIONS.md) · [Validation](docs/VALIDATION.md) · [Official references](docs/SOURCES.md).

## Production qualification and package distribution

This repository is not yet production-qualified. Read the [readiness assessment](docs/PRODUCTION-READINESS.md) and [prioritized TODO acceptance gates](TODO.md). The update supplies a full-build workflow plus [manual development SNAPSHOT publishing](docs/PACKAGING.md), not a stable release approval.

See [Spring-native alternatives and reuse boundaries](docs/SPRING-COMPARISON.md) and the [prepared Codex implementation task](docs/CODEX-HANDOFF.md). Generated configuration and passing offline checks are not evidence that GitHub Actions, Oracle or package consumption have executed.
