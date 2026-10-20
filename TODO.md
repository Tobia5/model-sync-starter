# Production qualification backlog

Reviewed 2026-10-20 against main `a555ffaf8681b6dcc2597f16a84bf5b2a45c11d6`.
These are **acceptance gates, not a claim that the project is production-ready**.
Owners below are suggested responsibilities; no person has accepted an assignment.
See [assessment](docs/PRODUCTION-READINESS.md), [packaging](docs/PACKAGING.md), and [worker task](docs/CODEX-HANDOFF.md).

## Implemented in the delivered patch; execution still required

- [x] Supply arc42, user handbook, production runbook, explicit job/event/visit APIs and ON_DEMAND recurrence.
- [x] Supply a read-only PR/main workflow and separate guarded, manual development SNAPSHOT publishing workflow.
- [x] Supply sources/Javadoc attachment profiles, GitHub Packages destination and sample deployment exclusion.
- [x] Supply offline publication guard/report-accounting tests and contributor guidance.
- [ ] Merge through review and obtain a green full build at the actual merged SHA.
- [ ] Publish and consume a development snapshot through a real clean consumer build.

## P0: blockers for production use

### R01 — Build the actual repository, not only a reconstructed fixture
Suggested owner: maintainer/build engineering.
- [ ] Run Java21 and the pinned Boot platform with `mvn -B -ntp -Plibrary-artifacts clean verify`.
- [ ] Fix real compiler, framework, AOP, auto-configuration, JPA/H2 and Reactor failures without disabling tests.
- [ ] Require nonempty, unskipped Surefire reports and preserve logs. Keep Oracle results separately identified.
- [ ] Make verification a required branch check; restrict workflow-file changes and protect main.
Acceptance: reproducible green Actions run on the review commit and on the merge commit, with exact dependency/JDK versions.

### R02 — Real Oracle concurrency and transaction proof
Suggested owner: persistence maintainer + DBA/QA.
- [ ] Test two independent connections/processes enqueueing one new subject, claiming different subjects, recovering expired leases and rejecting stale completion.
- [ ] Test JDBC ledger + JPA projection rollback together, racing inserts, pessimistic/optimistic conflicts and configured transaction-manager datasource enlistment.
- [ ] Test quota registration races, shared cooldown and admission across pods; include missing/misconfigured quota and database outage.
- [ ] Set and test statement, lock and transaction timeouts; a fetch timeout does not bound database waits.
Acceptance: all scenarios pass against the deployed Oracle major version/JDBC driver, with bounded completion times. H2 and in-memory synchronization are not substitutes.

### R03 — Schema migration and rollback/recovery
Suggested owner: DBA + maintainer.
- [ ] Exercise v1 to v2 with WAITING, LEASED, DEAD and pending-generation records; validate preserved data/indexes/constraints.
- [ ] Exercise stop/migrate/start ordering, interruption and backup restoration. Document why an old application cannot read IDLE without compatibility support.
- [ ] Use versioned application-managed migrations; do not add automatic production DDL to starter startup.
Acceptance: reviewed migration evidence from a disposable Oracle clone; backup and restore rehearsal; a documented supported rollback path (not a promise that every DDL is reversible).

### R04 — Scale, quota economics and provider isolation
Suggested owner: runtime maintainer + performance QA.
- [ ] Seed a representative million-record ledger and skewed hot-key workload; run several pods with constrained Oracle pool sizes.
- [ ] Measure DB queries/locks per admitted owner call, denied-admission churn, memory/threads, p95/p99 queue/visit latency and oldest pending age.
- [ ] Inject 429/Retry-After, slow/hung blocking calls, network reset, database loss, pod kill and shutdown during fetch/commit.
- [ ] Budget physical calls, including adapter-internal retries, MDM lookups and pagination; prove bursts respect the provider's actual contract.
- [ ] Confirm whether urgent quota reservations and per-owner bulkheads are required. Weighted priority selection alone does not supply these guarantees.
Acceptance: agreed resource and freshness SLOs met at target volume; no stale lease writes, runaway memory, busy-spin on cooldown or low-priority starvation. Record measured limits instead of inventing throughput guarantees.

### R05 — Observable, recoverable operation
Suggested owner: runtime maintainer + operations.
- [ ] Expose bounded-cardinality metrics for due/waiting/leased/dead work, oldest age, last successful cycle, lease reclaim, quota wait, physical calls, owner status/latency and conflicts.
- [ ] Add worker liveness/readiness signals and test alerts for stuck work and unavailable DB/provider. Do not fail the entire hosting service merely because one optional owner is down without a deliberate policy.
- [ ] Provide authorized DEAD inspection/replay with reason, actor and audit, preserving cooldown and source intent.
- [ ] Test graceful drain/cancellation/lease recovery and connection/thread pool exhaustion.
Acceptance: a second operator can diagnose and recover a forced incident from documented metrics/runbooks, without direct ad hoc edits to business tables. Never tag metrics with customer IDs or unbounded tenants.

### R06 — Trust boundaries and delivery correctness
Suggested owner: application/security maintainer.
- [ ] Enforce binding/tenant/subject authorization before request/event/visit admission and replay; validate untrusted event routing and identifiers.
- [ ] Configure TLS, credential storage, request/response limits, timeouts and error redaction for every adapter; constrain endpoint configuration and SSRF exposure where dynamically supplied URLs exist.
- [ ] Test cache isolation and fail-closed behavior on invalid identity, schema or authority configuration. Source events must not assert arbitrary owner data without validation.
- [ ] Resolve the contract for event arrival versus already-fresh local reads: eventual freshness is not strict read-after-change consistency.
Acceptance: threat model and negative tests for cross-tenant reads/refresh/replay, missing authorization and untrusted source data. Document application responsibilities instead of claiming that role qualifiers are security permissions.

## Scope-dependent release gates (P0 when the capability is promised)

### R07 — Complete-population reconciliation / boundary inventory
- [ ] Build a paginated inventory/delta importer with durable cursors, stable snapshot boundaries and atomic page-acceptance/checkpoint transactions.
- [ ] Test crash at page boundaries, duplicates, late changes, remote-only IDs and source tombstones.
- [ ] Define completeness when no inventory or delta feed exists; count unresolved/missing coverage in manifests.
Acceptance: reconciled coverage matches a known test snapshot after restart. Draining known IDs never counts as proof of discovering all external records. Evaluate Spring Batch for this lane before inventing a second checkpoint engine.

### R08 — Broker integration
- [ ] Persist inbound event identity/version as needed for duplicate and out-of-order handling; coordinate it with enqueue in a local transaction.
- [ ] Acknowledge Kafka/JMS/AMQP only after that transaction commits; handle rollback, redelivery and poison messages.
- [ ] Confirm custom event multicaster/executor behavior and AOP lock order; do not turn same-transaction work into an unrecorded asynchronous listener.
Acceptance: crash-after-commit-before-ack and replay tests lose no intent; older events cannot regress newer state. Coalescing is not exactly-once message delivery.

### R09 — IBM MDM and Vista/DWH adapters
- [ ] Implement real source-ID/MDM-ID resolution, mapping revision, merges/splits and unresolved/ambiguous handling; decide historical-fact identity rules.
- [ ] Implement target delivery/checkpoint/outbox when remote delivery is needed, rather than pretending an in-process transaction covers remote writes.
- [ ] Verify dataset grain, transformation version, snapshot cutoff, missing coverage and accepted/applied/visible acknowledgements.
Acceptance: contract tests with actual supported product protocols and synthetic identity changes; expected counts/digests at equal snapshot/grain. Existing interfaces are not complete vendor connectors.

## P1: safe package distribution and maintenance

### R10 — Exercise publication and independent consumption
- [ ] Configure the `model-sync-snapshots` GitHub Environment with required reviewers and branch restrictions; environment naming alone does not enforce approval.
- [ ] Run the manual snapshot workflow after CI passes; inspect all parent/runtime/starter POMs and artifacts. Verify the sample was not published.
- [ ] Compile and start a separate MVC/JPA and WebFlux/JPA consumer from an empty Maven cache using registry credentials, not the producer reactor.
- [ ] Document GitHub Maven authentication even for public packages; use a repository-scoped workflow token where supported or a properly scoped consumer token.
Acceptance: clean external consumer succeeds; attach publication and consumer evidence. A ZIP of JARs uploaded as an Actions artifact is not a Maven repository.

### R11 — Stable release and supply chain
- [ ] Define supported Boot/JDK/Oracle ranges, semantic versioning, API compatibility tests and migration policy.
- [ ] Pin a Maven Wrapper with verified checksums; verify repeatable clean builds and artifact checksums/provenance.
- [ ] Generate a release SBOM, run dependency/security/license analysis and record triage, not only a scanner installation.
- [ ] Create a protected tag-based release lane only after qualification. Do not rename SNAPSHOT to 1.0 to imply production approval.
- [ ] For Maven Central, verify ownership of the chosen group namespace (the existing `io.modelsync` is not proof of domain ownership), signing and current Central Portal requirements.
Acceptance: two independent clean consumer builds and complete release evidence; releases are immutable and samples remain excluded. `deployAtEnd` does not make remote multi-module uploads atomic.

### R12 — Mapping evolution, retention and audit
- [ ] Version source-owned mappings/fingerprints and invalidate/reconcile state on mapping changes.
- [ ] Define retention of cache payload, identity references, reconciliation state, inbound dedupe and audit separately.
- [ ] Implement bounded purge with racing refresh/visit tests and authoritative-deletion policy. Expired serving eligibility alone is not physical deletion.
- [ ] Provide auditable history where needed; MS_STATE is latest state, not a full historical audit ledger.
Acceptance: a mapping upgrade and purge do not leave data falsely trusted, resurrect deleted data or lose dedupe/identity history prematurely.

## P2: simplify extension and adoption

### R13 — Reuse existing Spring facilities where they fit
- [ ] Evaluate Spring Batch inventory/checkpoint integration, Spring Integration ingress/idempotency/message-store adapters and Spring Modulith publication integration.
- [ ] Keep owner authority, reconciliation comparison, freshness and admission semantics explicit; do not rewrite the current runtime wholesale without measured benefit.
- [ ] Consider a dependency BOM, a compatibility matrix, provider-specific auto-configuration tests and a minimal two-bean getting-started sample.
Acceptance: demonstrate equivalent failure semantics before swapping infrastructure; do not describe cache TTL or Envers auditing as remote reconciliation.
