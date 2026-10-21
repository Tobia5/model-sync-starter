# Production qualification backlog

Reconciled on 2026-10-21 on the review branch against the source-bound qualifications below; the original main assessment was `a555ffaf8681b6dcc2597f16a84bf5b2a45c11d6`.
These are **acceptance gates, not a claim that the project is production-ready**.
Owners below are suggested responsibilities; no person has accepted an assignment.
See [assessment](docs/PRODUCTION-READINESS.md), [packaging](docs/PACKAGING.md), and [worker task](docs/CODEX-HANDOFF.md).

**First supported scope:** known-ID owner-to-Oracle/JPA reconciliation in one correctly enlisted local datasource/`JpaTransactionManager`. The application supplies its owner reader, mapping, identity authorization and provider policy. Complete-population inventory, broker delivery, vendor connectors and remote target writes are outside this first scope. Their future gates remain open; interfaces do not constitute implemented connectors.

**B1 (preserved baseline):** source `4f440807f20c568a2240ed5ce32f1aab8e9558b0`, tree `02faa6e6b9b5b8a28af6234d2b1de699a736b735`, full run `3177a6c3-3b64-4e5b-b40b-dc18bf1c7884`, attempt `99a77d95-2499-4964-95d9-8e4ecf67d84d`. [Immutable report](https://github.com/Tobia5/model-sync-starter/blob/a67966d16ccf6c140f4c95bebc2ddfd3ad497364/QM.md), [raw evidence](validation/qualification/2026-10-20/raw-evidence.zip), SHA-256 `ab7ff892a1d8ceafab878deb977d1eacb7becf53bdca5f3dab71ef06eebbf92c`. B1-marked leaf criteria are completed only within that measured scope; Q2 leaves are identified separately. **R01–R13 parent gates remain open.**

## Implemented in the delivered patch; execution still required

**Q2:** source `1a9187ce0886912c14a2f77dbcccd3287a37e4a8`, tree `0276124dd146d4fc9ae89dfe5ebddf7154840374`; default run `83d7f773-3c8e-4a21-91a8-d6b90453253f` (61 JUnit tests), full run `8a2204e2-1371-4cce-8b7a-7ee5c573ae14`, attempt `a47ddfb5-db5b-42f9-bb2e-7f2b86b79b2e` (63 Surefire / 13 Oracle tests, zero failures/errors/skips). [QM](QM.md) and [raw artifacts](validation/qualification/2026-10-21-atomicity-queue/README.md) retain Q2, the separate diagnostic and all later failed attempts. The full configured suite passed; **all parent gates remain open**.

- [x] Supply arc42, user handbook, production runbook, explicit job/event/visit APIs and ON_DEMAND recurrence.
- [x] Supply a read-only PR/main workflow and separate guarded, manual development SNAPSHOT publishing workflow.
- [x] Supply sources/Javadoc attachment profiles, GitHub Packages destination and sample deployment exclusion.
- [x] Supply offline publication guard/report-accounting tests and contributor guidance.
- [ ] Merge through review and obtain a green full build at the actual merged SHA.
- [ ] Publish and consume a development snapshot through a real clean consumer build.

## P0: blockers for production use

### R01 — Build the actual repository, not only a reconstructed fixture
Suggested owner: maintainer/build engineering.
- [x] Build Java21 / Boot 4.1.1 with the pinned wrapper: `./mvnw -B -ntp clean verify`; B1 used the explicitly approved project Central settings, exit 0 (50 JUnit tests). Packaging is separate from verification.
- [x] Dependency-linked compiler, framework, AOP, auto-configuration, JPA/H2 and Reactor tests pass without disabling tests: B1 default run `f50960a1-2cec-438d-90ca-3ea6d70cdc30`.
- [x] Require and retain nonempty, unskipped reports: B1 full has 52 Surefire / 6 Oracle tests, zero failures/errors/skips, plus PIT 133/135 killed and six semantic Gatling scenarios. Oracle evidence remains separately identified.
- [ ] Make verification a required branch check; restrict workflow-file changes and protect main.
Acceptance: reproducible green Actions run on the review commit and on the merge commit, with exact dependency/JDK versions.

### R02 — Real Oracle concurrency and transaction proof
Suggested owner: persistence maintainer + DBA/QA.
- [x] Two independent Oracle connections coalesce one new subject, and reclaim rejects the old token: B1 `OracleConcurrencyIT`, 2 tests, 0.772 s. This is not independent-process/multi-pod proof.
- [ ] Independent processes claim different subjects and demonstrate crash/restart fencing under contention.
- [x] Prove real JPA projection + JDBC ledger rollback after flush, during completion, before commit and at physical Oracle COMMIT, with fresh-connection reads and separate retry metadata; preserve local-version and old-lease checks: Q2 `OracleAtomicityIT`, 7 passed / 11.95 s through the actual starter `JpaTransactionManager`.
- [x] Reject a different transaction-manager datasource before schema access: Q2 default `TransactionEnlistmentTest`; six portable H2 atomicity regressions also passed. This is not multi-datasource/XA qualification.
- [ ] Extend to racing inserts and actual pessimistic/optimistic exception races, independent-process crash/restart and arbitrary supported host configurations.
- [ ] Test quota registration races, shared cooldown and admission across pods; include missing/misconfigured quota and database outage.
- [ ] Set and test statement, lock and transaction timeouts; a fetch timeout does not bound database waits.
Acceptance: all scenarios pass against the deployed Oracle major version/JDBC driver, with bounded completion times. H2 and in-memory synchronization are not substitutes.

### R03 — Schema migration and rollback/recovery
Suggested owner: DBA + maintainer.
- [x] Exercise v1 to v2 preserving WAITING/DEAD evidence and enabling IDLE: B1 `OracleMigrationIT`, 1 test, 3.747 s, isolated migration schema.
- [ ] Extend migration proof to LEASED and pending-generation records, preserved indexes/constraints, interruption and rollback compatibility.
- [ ] Exercise stop/migrate/start ordering, interruption and backup restoration. Document why an old application cannot read IDLE without compatibility support.
- [x] Supply versioned v1-to-v2 migration and startup schema validation; no automatic production DDL: B1 migration test and `JdbcWorkStore.verifySchema` / JDBC auto-configuration.
Acceptance: reviewed migration evidence from a disposable Oracle clone; backup and restore rehearsal; a documented supported rollback path (not a promise that every DDL is reversible).

### R04 — Scale, quota economics and provider isolation
Suggested owner: runtime maintainer + performance QA.
- [x] Seed one million intents (100k due / 900k future), coalesce 100 hot-key requests, and run four workers with pool 8: B1 `OracleCapacityIT`, 211 persisted completions from 232 controlled stub calls. One JVM, not several pods; one million seeds are not one million completions.
- [ ] Run several pods at representative distributions and agree sustainable capacity/resource bounds.
- [x] Retain baseline cost/latency counters: B1 337 measured attempts, 222 stub calls / 201 completions, 34,237,829 summed block-work units, p95/p99 run-one latency 899/927 ms. These are not query counts, visit latency or a freshness SLO.
- [x] Capture individual claim/SQL counters, actual plans/waits and quota-denial churn, with separate seed/measurement/cleanup phases: Q2 8 distributions / 24 samples on 100k rows and unchanged 1M fixture, 360 measured attempts / 222 grants / 138 denials / 201 completions. About 99.93% of measured logical gets belonged to claim selection. B1 and failed attempts remain separate; no production SQL/index optimization is claimed.
- [ ] Qualify oldest-age/freshness SLOs and sustainable provider/DB economics at production-like statistics/distributions and multi-pod resource bounds; a million seeds and a completed sample do not establish this gate.
- [x] Inject synthetic 429/timeouts and 5/25 ms latency: B1 Oracle workload (13 rate limits / 8 timeouts); local semantic Gatling cooldown recovery. No real provider or hung-call proof.
- [ ] Inject hung calls, network reset, database loss, pod kill and shutdown during fetch/commit.
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
- [x] Pin Maven Wrapper 3.9.9 with distribution integrity and Windows support; clean default/full builds and source-bound artifact hashes: B1. Independent reproducibility/provenance review remains open.
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
