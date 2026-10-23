# Production qualification backlog

Reconciled on 2026-10-22 on the review branch against the source-bound qualifications below; the original main assessment was `a555ffaf8681b6dcc2597f16a84bf5b2a45c11d6`.
These are **acceptance gates, not a claim that the project is production-ready**.
Owners below are suggested responsibilities; no person has accepted an assignment.
See [assessment](docs/PRODUCTION-READINESS.md), [packaging](docs/PACKAGING.md), and [worker task](docs/CODEX-HANDOFF.md).

**First supported scope:** known-ID owner-to-Oracle/JPA reconciliation in one correctly enlisted local datasource/`JpaTransactionManager`. The application supplies its owner reader, mapping, identity authorization and provider policy. Complete-population inventory, broker delivery, vendor connectors and remote target writes are outside this first scope. Their future gates remain open; interfaces do not constitute implemented connectors.

**B1 (preserved baseline):** source `4f440807f20c568a2240ed5ce32f1aab8e9558b0`, tree `02faa6e6b9b5b8a28af6234d2b1de699a736b735`, full run `3177a6c3-3b64-4e5b-b40b-dc18bf1c7884`, attempt `99a77d95-2499-4964-95d9-8e4ecf67d84d`. [Immutable report](https://github.com/Tobia5/model-sync-starter/blob/a67966d16ccf6c140f4c95bebc2ddfd3ad497364/QM.md), [raw evidence](validation/qualification/2026-10-20/raw-evidence.zip), SHA-256 `ab7ff892a1d8ceafab878deb977d1eacb7becf53bdca5f3dab71ef06eebbf92c`. B1-marked leaf criteria are completed only within that measured scope; Q2 leaves are identified separately. **R01–R13 parent gates remain open.**

## Implemented features and source-bound qualification

**Q2:** source `1a9187ce0886912c14a2f77dbcccd3287a37e4a8`, tree `0276124dd146d4fc9ae89dfe5ebddf7154840374`; default run `83d7f773-3c8e-4a21-91a8-d6b90453253f` (61 JUnit tests), full run `8a2204e2-1371-4cce-8b7a-7ee5c573ae14`, attempt `a47ddfb5-db5b-42f9-bb2e-7f2b86b79b2e` (63 Surefire / 13 Oracle tests, zero failures/errors/skips). [Pinned Q2 QM](https://github.com/Tobia5/model-sync-starter/blob/e73b3deba59aab0401512aa73329b14b8adbcadd/QM.md) and [raw artifacts](validation/qualification/2026-10-21-atomicity-queue/README.md) retain Q2, its separate diagnostic and contemporaneous failed attempts. The full configured suite passed; **all parent gates remain open**.

**Q3:** tested code `ede199a33ad3a0ec5c911110bb84fd7407af1c25`, tree `7c9577e9de49a2f96b1093a65673250a46163da6`; default run `d0ef4b54-0ed3-428b-bbd8-1d1b77d62a54` (103 JUnit tests / 55 Python guards), full run `2b75d9cc-8836-4e99-b227-9d836e7e207a`, attempt `c3a564fe-e255-4b85-a209-05244d9b86ec` (105 Surefire / 26 Oracle tests, zero failures/errors/skips). Configured local full PASS; production NO-GO. [QM](QM.md), [current default CI](https://github.com/Tobia5/model-sync-starter/actions/runs/39623846568) and [raw evidence](validation/qualification/2026-10-21-queue-optimization/README.md). Older passes, failures and source-changed attempts remain separate. All R01-R13 parents stay open.

**Q5 initial shutdown/recovery attempts (historical):** final code `8830c3f6d2ac8483c35309e747e896131c64fcea`, tree `10cb9835a8f925f9e45a2a437d9f637ee5668ca2`, default run `47311f17-5b2c-409b-ab19-8b9340fc8af5` PASS (117 JUnit /61 Python). Completed full tested `3564410ad8e884509cecb0e530a8e3c16473eb29`, tree `d0af02e4117463eae97a25108b537eb9650fd577`, run `d0500d1e-030b-425e-9b49-a35e214f7598`: FAILED,119 Surefire /45 Oracle tests,2 Oracle failures,0 errors/skips. Six process-crash and all seven atomicity cases passed; independent statement cancellation and the worker20s backlog gate failed. A later ten-phase worker diagnostic passed without changing pacing or gates. Final-code full rerun is BLOCKED before provisioning by the unchanged18GiB memory planning budget (17.49–17.85GiB available); no earlier reports are attributed to it. [QM](QM.md), [raw evidence](validation/qualification/2026-10-22-shutdown-recovery/README.md), [final-code default CI](https://github.com/Tobia5/model-sync-starter/actions/runs/39761845131). Accepted Q4 `fad6874` and prior evidence remain unchanged; all parent gates remain open and production remains NO-GO.

**Q5 resumed full after restored headroom:** source `08bd1906e54d6a182a1a7d2c9473fc255f7038fa`, tree `a5f5e717c9fcd3331c139558508c312aff901d60`; fresh default run `2bb7a10f-29a9-4b30-9313-48aa2d1960d9` PASS/117 JUnit/61 Python, exit0. Full run `e88fc1de-755e-470c-a7f7-15c3c7a4f13d`, attempt `3dabb75d-761d-427a-88f7-5126042f4b90`, actually executed after21.648GiB resource admission: FAILED,119 Surefire/45 Oracle tests,one Oracle failure,zero errors/skips, Maven/wrapper exit1. All six crash cases, seven atomicity cases, ten worker phases, PIT160/162 and six semantic Gatling scenarios passed. Independent statement cancellation still physically reading at5017ms with effective2s query timeout is the sole full failure. The earlier worker timeout is retained; this full repeat passed160 completions within20s in both modes. One million seeds produced200 measured completions/210 including warmup. Current container/volume independently absent; all eight session pairs have receipts. [Current QM](QM.md), [new raw evidence](validation/qualification/2026-10-22-shutdown-recovery-rerun/README.md). Production remains NO-GO; earlier reports/archives are unchanged.

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
- [x] Requalify the current review code with explicit default/full separation: Q3 default 102 goals with no PIT/Gatling/Failsafe, full PIT 160/162, all eight declared classes and six semantic Gatling scenarios. Exact source/tree and library build hashes are retained.
- [ ] Make verification a required branch check; restrict workflow-file changes and protect main.
Acceptance: reproducible green Actions run on the review commit and on the merge commit, with exact dependency/JDK versions.

### R02 — Real Oracle concurrency and transaction proof
Suggested owner: persistence maintainer + DBA/QA.
- [x] Two independent Oracle connections coalesce one new subject, and reclaim rejects the old token: B1 `OracleConcurrencyIT`, 2 tests, 0.772 s. This is not independent-process/multi-pod proof.
- [ ] Independent processes claim different subjects and demonstrate crash/restart fencing under contention.
- [x] Force termination of owned JVMs at acknowledged durable-claim, owner-response, real projection-flush and committed-completion checkpoints; inspect fresh committed state and recover with a fresh JVM after lease expiry, reject old tokens and preserve newer generations/local revisions: Q5 completed full3564410, six `OracleRecoveryIT` cases PASS/64.32s. This is scoped headless coordinator recovery, not multi-process claim contention or continuous-worker pod-failure qualification.
  - [x] Repeat all six cases in resumed full08bd190/run`e88fc1de-755e-470c-a7f7-15c3c7a4f13d`: PASS/63.011s, fresh-process recovery8235–8510ms, old tokens rejected, newer generation/local revision preserved, owned processes/pools ended.
- [x] Prove real JPA projection + JDBC ledger rollback after flush, during completion, before commit and at physical Oracle COMMIT, with fresh-connection reads and separate retry metadata; preserve local-version and old-lease checks: Q2 `OracleAtomicityIT`, 7 passed / 11.95 s through the actual starter `JpaTransactionManager`. Repeated unchanged in Q3: seven passed / 13.558 s.
- [x] Reject a different transaction-manager datasource before schema access: Q2 default `TransactionEnlistmentTest`; six portable H2 atomicity regressions also passed. This is not multi-datasource/XA qualification.
- [ ] Extend to racing inserts and actual pessimistic/optimistic exception races, contending independent processes and arbitrary supported host configurations; the six Q5 checkpoint recovery cases above close only their declared scope.
- [ ] Test quota registration races, shared cooldown and admission across pods; include missing/misconfigured quota and database outage.
- [ ] Set and test statement, lock and transaction timeouts; a fetch timeout does not bound database waits.
  - [x] Qualify fixture-owned connection acquisition/read stalls, cooperative transaction rollback, controlled JPA-lock stop and uncertain COMMIT request/acknowledgement outcomes using fresh connections: Q5 full3564410,11/12 `OracleIoIT` cases passed. Direct pool acquisition/reuse is not saturated-worker end-to-end proof.
  - [ ] Resolve independent locked-JDBC cancellation: effective query timeout2s, physical socket read still active at5001ms in the initial Q5 full and5017ms in resumed full08bd190/run`e88fc1de-755e-470c-a7f7-15c3c7a4f13d`, against unchanged5s/ORA-01013 gate. Passing2s network timeout does not close this failure; post-failure state assertions are not claimed passing.
    - Focused candidate `5b898c3`, tree `8d28ebead9413f98af9dc6a4385ed90b0c78f919`: default run `177ed7a8-1c51-459f-913b-74a22f48ecf9` PASS/117 JUnit/64 Python. Six one-row direct/Spring automatic/manual/network probes compiled; execution **BLOCKED / NOT_RUN** by12.185 GiB free versus retained18 GiB resource budget after Maven exit. No new cause/fix/containment proof; original gate and reviewed evidence unchanged. [Focused evidence](validation/qualification/2026-10-23-jdbc-cancellation/README.md).
Acceptance: all scenarios pass against the deployed Oracle major version/JDBC driver, with bounded completion times. H2 and in-memory synchronization are not substitutes.

### R03 — Schema migration and rollback/recovery
Suggested owner: DBA + maintainer.
- [x] Exercise v1 to v2 preserving WAITING/DEAD evidence and enabling IDLE: B1 `OracleMigrationIT`, 1 test, 3.747 s, isolated migration schema.
- [x] Apply additive recovery/binding-ready index migrations to an older-schema Oracle fixture, check exact columns/data and refusal to replace an existing index: Q3 `OracleMigrationIT`, 1 passed / 3.376 s in the isolated migration schema.
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
- [x] Compare baseline, selector-only, admission-only and combined in forward/reverse order on one 100k catalog with four workers/pool eight: Q3 `OracleOptimizationIT`, 1 passed / 500.062 s covering eight cases. Combined logical gets/completion 293.055/287.605 versus baseline 163316.378/155011; denials 5/3 versus 101/85. Selector-only was faster but had 616/659 denials; admission-only was slower. This is not a universal speedup/SLO.
- [x] Repeat eleven locking/fairness/recovery cases on H2 and Oracle, and retain 48 same-population legacy/selected probes across eight scale cases: Q3. Selected plans had no SORT; future-only gathered-stat probes still cost 1859 versus legacy 1710 logical gets. One million seeded intents produced 200 measured completions / 220 owner entries in 11096 ms; total 210 including warmup, not one million reconciliations.
- [x] Correct cursor reload/reset/disappearance attribution with six cheap regression cases and current source-bound raw endpoints: Q4 `fad6874`, tree `a530a260738510da84ed7f68fd26a8a75b803d67`, full run `1afffb7d-52ca-4d4a-8857-23c3cefb8f4c`; 1116 VALID/42 UNATTRIBUTABLE interval records. Old correction and raw baselines retained.
- [x] Measure the actual auto-configured worker/JPA/JDBC host, fixed250 versus productive5/idle250–1000ms, four lanes/pool8: Q4 full ten phases passed;160 measured completions per mode,18061/9467ms; warmed empty15.00/3.99 polls/s,3493/998.75 native gets/s. Local/independent urgent, future eligibility, cooldown and recovery probes passed the declared local budgets. [Q4 QM](QM.md), [raw evidence](validation/qualification/2026-10-22-worker-pacing/README.md). These are single-JVM small-probe results, not production capacity or general drain proof.
- [ ] Qualify sustained future-only/index-history/polling economics and arbitrary locked prefixes at production-like distributions/multi-pod bounds. No claim-batch benchmark was run; empty probes cannot yield a claim batch. Callback, native-session and cursor metrics remain separate; no universal database-work bound is claimed.
- [ ] Qualify oldest-age/freshness SLOs and sustainable provider/DB economics at production-like statistics/distributions and multi-pod resource bounds; a million seeds and a completed sample do not establish this gate.
- [x] Inject synthetic 429/timeouts and 5/25 ms latency: B1 Oracle workload (13 rate limits / 8 timeouts); local semantic Gatling cooldown recovery. No real provider or hung-call proof.
- [ ] Inject hung calls, network reset, database loss, pod kill and shutdown during fetch/commit.
  - [x] Exercise owned TCP stalls/reset/uncertain COMMIT, forced owned JVM termination and graceful/late-owner/locked-JPA stop with separate physical-operation and cleanup proof: Q5 scoped cases above; six crash cases and11/12 I/O cases PASS in the FAILED full. No database-wide loss or arbitrary partition claim.
  - [ ] Qualify silent partitions/streaming peers, whole-database loss, contending/multi-pod failures and saturated-worker pool/thread recovery on supported deployment configurations.
- [ ] Budget physical calls, including adapter-internal retries, MDM lookups and pagination; prove bursts respect the provider's actual contract.
- [ ] Confirm whether urgent quota reservations and per-owner bulkheads are required. Weighted priority selection alone does not supply these guarantees.
Acceptance: agreed resource and freshness SLOs met at target volume; no stale lease writes, runaway memory, busy-spin on cooldown or low-priority starvation. Record measured limits instead of inventing throughput guarantees.

### R05 — Observable, recoverable operation
Suggested owner: runtime maintainer + operations.
- [ ] Expose bounded-cardinality metrics for due/waiting/leased/dead work, oldest age, last successful cycle, lease reclaim, quota wait, physical calls, owner status/latency and conflicts.
- [ ] Add worker liveness/readiness signals and test alerts for stuck work and unavailable DB/provider. Do not fail the entire hosting service merely because one optional owner is down without a deliberate policy.
- [ ] Provide authorized DEAD inspection/replay with reason, actor and audit, preserving cooldown and source intent.
- [ ] Test graceful drain/cancellation/lease recovery and connection/thread pool exhaustion.
  - [x] Implement bounded claim-admission stop/drain with physical lifetime/capacity retained through reactive cancellation and blocked restart while unresolved; Q5 default regressions and controlled full Oracle/owner cases. Late owner stop correctly reported TIMED_OUT with logical0/physical1, then DRAINED after fixture release; no late application.
  - [x] Verify direct fixture Hikari exhaustion1008ms/reuse and owned process/pool/thread/socket cleanup: Q5 completed full. All seven session containers/volumes independently absent; final blocked attempt created none.
  - [x] Clear the unchanged18GiB resource prerequisite and execute complete full verification of08bd190:21.648GiB admission, same6GiB/4CPU Oracle caps, exit1 with fresh failed evidence and independently confirmed cleanup. This completes execution, not successful qualification.
  - [x] Repeat the unchanged full-context worker gate: resumed full08bd190 passed all ten phases,160 completions in17260/9737ms fixed/bounded pacing. Earlier failed full and diagnostic remain separately preserved; no pacing redesign or universal SLO claim.
  - [ ] Obtain passing full qualification after resolving independent statement cancellation (latest08bd190 full FAILED). Extend beyond direct fixture exhaustion to saturated-worker/shared-host conditions. All parent operational gates remain open.
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
