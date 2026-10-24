# Architecture: authority-aware model synchronization

> **Trigger-extension update:** for job/event/visit APIs, on-demand recurrence and current validation boundaries, use the [handbook](USER-HANDBOOK.md), [arc42 architecture](ARC42.md), [production runbook](PRODUCTION.md) and [review report](REVIEW.md). This document retains the original design/history; original test counts and schema-v1 examples are not the new release evidence.


## 1. Scope and architectural position

Model Sync is an **operational pull-reconciliation starter**, not an ORM replacement and not a generic replication database. Its default use case is: an external party owns a set of fields, the application stores a local replica or temporary cache, and the application needs bounded-staleness access and accountable reconciliation under a provider request budget.

The first implementation uses Oracle for durable coordination and JPA/Hibernate for application projections. Reactor is an execution adapter, not a core dependency. A synchronous owner and a native async owner use the same decision/commit semantics.

The package is a source reference implementation. The scope matrix and execution evidence are in `VALIDATION.md`; statements about behavior here describe the source and, where indicated, its offline contracts. They are not a claim of Oracle production verification.

## 2. Use class bindings as the source of truth

The central distinction is between **what a model means**, **which route is authoritative**, and **how bytes are stored**.

A Java `Customer` record may be used in several bounded contexts. Annotating it globally as `@DataOwner` would incorrectly imply that every use of that type has the same authority. Instead, a `ModelBinding<Customer>` declares an explicit, named route. It supplies the boundary, owner system identifier, owner reader, local role/store, field fingerprint, freshness policy, quota group and permission for local repair.

A binding has exactly one owner reader and exactly one local projection. A fan-out is expressed as separate named bindings or an analytical delivery extension, not an uncontrolled list of side effects hidden in one entity annotation. A route ID is stable application metadata. Its model class is a type token checked by the registry. It is not serialized to the database as a Java class name.

`ModelRegistry` rejects duplicate binding IDs and incompatible class lookup. `gateway.model(Customer.class)` is convenient when the type has a single route. `gateway.model("customer-operational", Customer.class)` is unambiguous when multiple routes share a DTO. `ModelSync<Customer>` can itself be a Spring bean and be constructor-injected into services.

### Roles

| Role | Allowed meaning | Explicitly not implied |
|---|---|---|
| `DataOwner` | Authoritative source for the fields mapped by this binding | Permission to write to the owner |
| `DataReplica` | Durable local representation | Independent truth or permission to overwrite source data |
| `DataCache` | Temporarily retained local representation | Freshness merely because a row still exists |
| `BoundaryOwner` | Optional catalog / enumerator for a bounded context | Authority over every field in every returned record |

The Java annotations with these names are Spring qualifiers only. The builder is the executable definition. Qualifiers improve wiring readability and permit independent customer/address/contract adapters without globally disabling defaults. Using ordinary `@Qualifier` or constructor wiring is equally valid.

## 3. Module graph

```text
application configuration + application DTO/entity mapping
                       |
             starter-oracle-jpa  (+ optional starter-webclient)
                       |
              base starter / autoconfigure
                       |
          +------------+-------------+
          |            |             |
        spring       reactor        rest / webclient
          |            |             |
          +------------+-------------+
                       |
                      core
                       ^
                       |
              persistence-jpa + persistence-jdbc
```

| Module | Responsibility |
|---|---|
| `model-sync-core` | Typed ports, route registry, decisions, freshness, tickets, dataset comparison |
| `model-sync-spring` | Qualifiers, REQUIRED transaction bridge, narrow request advisor |
| `model-sync-persistence-jpa` | App-owned DTO/entity mapping and version-aware local CAS |
| `model-sync-persistence-jdbc` | Oracle-first ledger, locking claims, shared quota, DB clock, manifest reader |
| `model-sync-rest` | Blocking `RestClientOwner` and shared HTTP result classification |
| `model-sync-reactor` | Bounded worker and lazy reactive gateway; blocking bridges |
| `model-sync-webclient` | Native async `WebClientOwner` |
| `model-sync-autoconfigure` | Conditional defaults, property validation, optional integration assembly |
| Three starter modules | Dependency aggregation; no business algorithms |
| `model-sync-testkit` | In-memory contract resources, controlled clocks and fault scenarios |
| `model-sync-sample` | Headless sample, real JPA mapping, optional REST/Oracle profiles |

The core imports neither Spring, Hibernate/JPA, JDBC, HTTP-client libraries nor Reactor. Its async capability is `CompletionStage`, supplied by the JDK. The source dependency check rejects internal cycles and framework imports in core. It is a structural check, not a complete static-analysis suite.

## 4. Ports and replacement points

`BlockingOwner<T>` / `AsyncOwner<T>` are read-only capabilities. A fetch receives a tenant-scoped subject key and an optional trusted validator. It returns a `FetchResult<T>`: found, not modified, or absent. Temporary errors are exceptions with classified retry semantics; they must not masquerade as absence.

`ProjectionStore<T>` loads immutable snapshots and performs compare-and-set updates. Its revision must change for every relevant concurrent local mutation. This port participates in the same local transaction as the ledger. A remote REST call must never be placed inside its methods.

`WorkStore` owns durable queue state and reconciliation state. `LocalTransactions` supplies REQUIRED local transaction boundaries. `QuotaGate` coordinates request admission and provider cooldown. `Clock`, `SyncOptions` and `WeightedPriorities` are injectable policies/infrastructure. The default quota is shared JDBC pacing rather than a per-pod in-memory counter.

`IdentityResolver`, `BoundaryInventory`, `DatasetPort`, `VistaPort` and `DwhPort` are integration seams. Their presence does not automatically register new model bindings or schedule external enumeration. The application explicitly wires and invokes them. Keeping that boundary explicit avoids confusing an interface declaration with a working vendor integration.

Singleton infrastructure defaults back off with `@ConditionalOnMissingBean`. Named model adapters are selected by the application binding, not by a single broad condition such as “there is no bean of type owner anywhere.” Named schedulers and the clock can also be replaced. Auto-configuration uses `AutoConfiguration.imports`, not application-wide component scans. See official reference S2.

## 5. Identity and IBM MDM

The durable work identity is `(binding ID, tenant, subject ID)`. `WorkKey` derives a SHA-256 identifier using unambiguous length-prefixed UTF-8 fields. A unique database constraint on the original tuple also protects the logical identity. The opaque ticket is an identifier, not a confidentiality mechanism.

For an external data-owner route, the subject ID should normally be the stable **owner-system record ID** or an application-stable reference whose mapping is controlled by the application. Do not assume a canonical MDM ID is immutable across all merge/split processes or identical to the ID accepted by every owner endpoint.

`IdentityResolver` represents resolved, unresolved and ambiguous mappings, aliases and a mapping version. A concrete IBM MDM adapter must implement the actual deployed API/version, tenancy, merge/split policy and authoritative reference semantics. None of those protocols are guessed in this package.

A mapping change should invalidate affected projections and schedule their bindings. Do not silently move all rows to a new MDM key or interpret an ambiguous resolution as deletion. In the initial version, mapping persistence/invalidation is application responsibility; there is no automatic MDM event subscriber.

Each physical MDM request needs its own provider budget. The built-in coordinator budgets one owner fetch. Calling MDM and then the owner inside one custom fetch without additional admission is not covered by that single permit. Prefer pre-resolved references or a separately budgeted mapping stage/binding.

## 6. Data and state lifecycles

The application owns its business entity/table. The library owns a small durable ledger:

| Table | Purpose |
|---|---|
| `MS_META` | Explicit schema version |
| `MS_WORK` | Coalesced durable subject work, priority, retry/due time, generation, lease |
| `MS_STATE` | Last authoritative validation, last successful match, digest/validator/outcome |
| `MS_QUOTA` | Shared admission spacing and next allowed time/cooldown |

There is no large in-memory queue. Work is stored at subject granularity, so repeated requests for the same subject do not create an unbounded event log. Every new request increments a generation. If a request arrives while a worker is active, completion sees the newer generation and retains follow-up work.

A ticket identifies that subject's work slot. `status()` exposes the last reconciliation state, not proof that a particular request generation has completed. Detailed per-request receipts, immutable history and distributed await semantics are not implemented in this version. Applications that need them should add a separate append-only receipt/audit model rather than reinterpret a coalesced ticket.

Queue state is `WAITING`, `LEASED` or `DEAD`. Successful reconciliation returns a subject to `WAITING` with its next periodic due time. Therefore a successful subject still having a waiting work row is intentional. Unless a newer request is pending, periodic work returns to `BACKGROUND` priority.

Outcomes such as `MATCH`, `APPLIED`, `NOT_MODIFIED`, `MISMATCH` and `OWNER_ABSENT` are reconciliation results. `DEAD` is a work-execution status. They are not interchangeable. A source failure does not change an old match into a new successful verification.

## 7. Decision table: what does the synchronizer call?

The runtime chooses only among declared capabilities. It does not reflectively guess a `getAll`, `save` or `delete` method.

| Situation | Decision |
|---|---|
| Exact local snapshot, trusted state, age below refresh threshold, not forced | Complete/reschedule locally; no owner call and no age reset |
| Verified local snapshot due for refresh | Obtain quota, send validator when available |
| Missing, physically expired or locally changed snapshot | Obtain quota, fetch unconditionally |
| Owner returns found and fields match | Record current authoritative validation/match; optionally renew cache retention |
| Owner returns found and fields differ; verify-only | Record mismatch; leave local data unchanged |
| Owner returns found and fields differ; local repair enabled | CAS-update only mapped owner fields and ledger atomically |
| Owner returns not modified without a valid matching local snapshot | Permanent adapter/protocol failure; no fabricated match |
| Owner returns absent | Record/quarantine absence; do not delete automatically |
| Local revision changes while HTTP is in flight | Reschedule as local conflict; do not overwrite a newer snapshot |
| Lease token no longer valid | Discard late completion |
| Quota denied or provider returns 429 | Persist defer/cooldown; do not renew freshness or spend retry-failure allowance |

`force=true` bypasses the fresh-cache shortcut, not quota, lease or ownership checks. It may still use a valid conditional request; force is not a promise of a full payload. To change transformations, include an explicit mapping version in the fingerprint and deliberately reschedule affected subjects.

## 8. Transaction choreography and crash safety

```text
Caller / @RequestSync
  begin REQUIRED local transaction
  enqueue / lock durable work intent
  perform local business mutation, when applicable
  commit both

Worker
  TX 1: claim bounded work -> opaque lease token -> commit
  TX 2: lock/validate lease -> load state + immutable projection -> commit
  acquire independent shared request admission
  HTTP or external owner I/O OUTSIDE all local transactions
  TX 3: lock/validate lease -> re-read local version -> optional CAS repair
        -> update ledger and next due work -> commit together
```

No managed JPA entity crosses the external-call boundary. The immutable DTO plus local revision travels with the work plan. The entity is reacquired in the completion transaction.

The worker's commit lock order is ledger before business projection. The provided AOP advisor obtains the work lock before entering the annotated business method. Programmatic application transactions should use the same order. A caller that already holds projection locks before requesting work can reintroduce a deadlock cycle; arbitrary outer transactions or unrelated business writers are not automatically made deadlock-free. Database deadlock/lock-timeout handling and business idempotency remain deployment concerns.

Projection changes and ledger changes use a single database/local transaction manager. JPA is appropriate for mapping; JDBC is appropriate for queue coordination. `JpaTransactionManager` supports JDBC access to the same datasource within its transaction (S3). The default startup check requires the selected transaction manager's datasource to be the exact ledger datasource. XA/custom managers require explicit replacement infrastructure.

The default work model gives **at-least-once execution with fenced, idempotent local effects**, not end-to-end exactly-once networking. A worker can consume a permit and crash before a request, or receive a response and crash before commit. Retrying a read is safe under the read-only owner contract. A future remote write must add destination idempotency and durable delivery receipts.

A valid lease must outlive the HTTP timeout and expected local work. The default is 60 seconds versus a 15-second fetch timeout, with configuration validation. There is no lease-renewal daemon. Late results are fenced rather than applied after lease loss.

## 9. Cache age, maximum age and retention are separate

`refreshAfter` is the target age at which a trusted subject becomes due. `maxAge` is the serving limit enforced by `ModelSync.local()`. `retention` is physical cache lifetime; it is not a claim of correctness. These are durations, not guessed change frequencies.

`validatedAt` records a successful authoritative response, including an observed mismatch or absence. `reconciledAt` records a confirmed match/applied repair. `matchedDigest` identifies exactly which local mapped content was matched. `sourceChangedAt` is only a source-supplied timestamp when available; it is not manufactured from fetch time.

A payload whose current fingerprint differs from the last matched digest is unverified even if the prior validation timestamp is recent. A freshly fetched mismatch is fresh evidence, not reconciled local data. A future validation timestamp is treated as unknown, not negative age.

`local()` enforces both trust and maximum age. `freshLocal()` is stricter. `inspect()` is diagnostic. Direct application repository access bypasses these serving rules; the starter does not weave freshness checks into JPA getters.

Cache eviction removes payload, not the ledger. A subsequent request fetches unconditionally because an ETag alone cannot reconstruct an evicted object. An application cleanup job must respect legal retention and row ownership. No implicit delete-on-404 or external cascade delete is implemented.

Age scheduling covers subjects already registered in `MS_WORK`. It does not scan every application table automatically. Initial seeding, missed-write reconciliation and source enumeration need explicit bounded ingestion.

## 10. Priority, backpressure and provider budgets

The coordinator uses four priority classes with default weights 4:3:2:1. Selection is work-conserving: if the chosen class is empty, other classes are considered. Successful periodic work does not remain urgent forever. Repeated requests coalesce and may promote priority.

This is **weighted claim selection**, not globally strict priority across pods, not preemption of running HTTP, and not a guaranteed per-class share of successful provider calls. Contention and quota denials can skew realized throughput. Strict urgent reservations, global per-class budgets and deadline/age promotion are extension work, not features silently implied by the name priority.

The Reactor worker has a bounded number of execution lanes. Each lane processes at most one leased subject at a time. It does not preload millions of tasks into Reactor buffers. The persistence scheduler and blocking-owner scheduler are distinct. The default blocking-owner pool is shared across providers; stronger per-provider bulkheads/circuit breakers require additional routing or a custom worker.

`JdbcQuotaGate` serializes short admission transactions on a quota row. All cooperating pods share the same pacing/cooldown state. It is a smooth admission limiter, not an adaptive token bucket or a provider-contract parser. The default does not refill burst tokens. Queueing after admission and network effects still mean request arrival timing is not mathematically identical to permit timing; use provider-specific margin.

One standard owner invocation is one request. The supplied adapters do not intentionally paginate or add retry operators. Custom client retry interceptors, redirect behavior, bulk cost accounting, MDM lookups and additional internal requests must be included in your physical-call budget. Nonparticipating services using the same credential also consume the provider limit.

## 11. Reactive and synchronous applications

A Boot MVC application, WebFlux application and headless worker may use the same core. The async core port is `CompletionStage`; `ReactiveOwners.from` bridges a `Mono`-returning function. `WebClientOwner` is a native async source. `RestClientOwner` is blocking.

`ReactiveModelSync` defers local blocking gateway calls until subscription and executes them on the dedicated persistence scheduler. `ReactorSyncWorker` executes local transactions there, routes synchronous owner work to the owner scheduler, and does not block Netty/event-loop threads with JPA. This follows Reactor's documented blocking-wrapper pattern (S5).

The statement is **WebFlux-compatible**, not “reactive JPA.” Entity managers remain transaction/thread-bound. Timeout is applied to the fetch stage, not around a database commit. Cancellation attempts to cancel asynchronous work, but it cannot undo an already sent network request or guarantee interruption of every blocking client. Lease fencing handles late completion.

## 12. Boundary owner and completeness

`gateway.boundaryModels("cards")` answers: which model bindings did this application register for the cards boundary? This is deterministic local metadata.

`BoundaryInventory.models()` answers: which external model types does an explicit provider adapter expose? `enumerate(model, tenant, cursor, limit)` answers: which subject IDs belong to a provider page/snapshot? The interface carries a snapshot ID, continuation cursor and completion flag.

A durable enumeration engine is not shipped here. A correct extension must atomically enqueue a page and persist its cursor in a checkpoint table, bind cursor validity to the snapshot, restart safely after crashes, and gate every enumeration call. Do not advance a cursor before durably accepting the page. Do not use an unbounded `getAll()` collection for millions of records.

Without that capability, this starter can reconcile known IDs but cannot establish source-universe completeness or discover arbitrary remote-only records. An operational dashboard must label coverage accordingly.

## 13. Vista and DWH: dataset reconciliation, not record fan-out

The analytical seam operates on a `DatasetSlice`: dataset, tenant, snapshot, partition, mapping version and record grain. A manifest contains that actual slice identity, row count, unresolved-record count, business totals and a deterministic content digest.

The default comparison returns `INCOMPARABLE` for a different slice, `INCOMPLETE` for unresolved coverage or missing digests, `DIFFERENT` for unequal content/counts/totals, and `MATCH` only when the comparable evidence agrees. Numeric totals use value comparison, not BigDecimal scale equality.

`JdbcDatasetReader` reads a reviewed parameterized manifest query and checks the identity actually returned from the database; it does not label any returned result with the requested snapshot and pretend it matches. Null count fields are rejected. Users supply schema-specific SQL.

`VistaPort` and `DwhPort` are typed reader interfaces. Vendor HTTP protocols, warehouse loading, durable dispatch, partition-digest generation and drill-down orchestration are not implemented. `DatasetSink` describes a delivery seam with distinct accepted/applied/verified receipts; the starter does not claim a completed outbox implementation.

MDM uniqueness depends on the dataset grain. One row per transaction may legitimately repeat customer IDs. Compare like-for-like snapshots and preserve unresolved/excluded coverage rather than dropping failed subjects and declaring the reduced dataset reconciled.

## 14. Important exclusions and next design increments

The initial engine deliberately does not implement automatic entity scanning, Envers/CDC capture, remote writes, bidirectional merge, automatic deletion, a durable source-inventory importer, generic JPA reflection mapping, global strict deadline scheduling, immutable audit history, or a ready-made operations UI.

The next increments should be driven by actual provider contracts: a separately budgeted IBM MDM mapping stage; durable inventory/delta checkpoints; provider-specific bulkheads; snapshot manifests; and, only when required, an idempotent delivery outbox. They should remain separate modules and ports rather than changing the core model into a Hibernate event listener with network side effects.
