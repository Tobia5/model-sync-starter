# First-release acceptance matrix

This is the current acceptance record. Earlier dated reports and the previous TODO
remain immutable history. The supported first release is **known-ID owner-to-Oracle/JPA
reconciliation**, with concurrent compliant ledger-first writers, one correctly
enlisted local datasource/JpaTransactionManager, detached DTOs and explicitly
qualified mappings. Inventory, brokers, vendor connectors, owner writes and
arbitrary triggers/additional flush targets are outside that promise.

**Production decision: NO-GO.** Completed execution leaves below are complete;
open production subcriteria are not replaced by a configured integration PASS.

## Evidence keys

- **F** — accepted named-target full source `a0f46d7a6f188263f8822e3ad728e1894076871a`,
  tree `0382fbeb887e3b3c2a55533f8ebc65cf522acb51`, run
  `751ea112-4068-48a0-97fc-c12605a59802`, report `8d07aa7`:
  [immutable results](../../validation/qualification/2026-10-26-integrated-repaired-target/README.md).
  Default `c965b470-292f-4346-b739-00048a7163f5`:222 JUnit/102 Python.
  Full:224 Surefire/83 Oracle methods in18 suites, all required checks PASS.
- **C** — independent local artifact source `551427bed4d021339fcc32fef8f633232ff8deb8`,
  tree `892daec99e12738607a9bfe736d8e98ee8218d0c`, packaging/default run
  `b3db2e0f-e56a-420f-b379-0d1e73f15eb0`, consumer run
  `ca8ae33e-62af-4cc5-935d-cc691de61f60`: [results and raw evidence](../../validation/qualification/2026-10-26-independent-consumers/README.md).
  Default222 JUnit/112 Python, 39 staged files; MVC and WebFlux each1 JUnit,
  3 physical owner calls/3 committed reconciliations. All exits0. Full NOT_RUN
  in this task; F retains its exact tested source. Runtime implementation is
  unchanged from F; the sole production-source edit corrects a Javadoc parameter.
- **S** — consumer security fixture `8df8c949b8ddd570cf723e7f7ab522c9aab2e3f2`, tree `6bf4d2531e1c42d067f4db99ad844f87f4a8430e`:
  default `f87f69d3-16fa-466b-b2e9-25031ab0b0b7` (222 JUnit / 116 Python) and 5 MVC / 6 WebFlux
  cheap security methods PASS. The 2026-10-26 Oracle attempt `47b66c3c-6d1e-425e-bedb-ebd7d1438ec2`
  BLOCKED at host-memory admission, journeys NOT_RUN. Earlier source 8e36028/run
  `143adeff-77ef-4864-ad9c-23ab46534c6c`: overall FAILED, WebFlux PASS
  (30 denials/four outcomes/32 concurrent identities), MVC fixture assertion FAILED.
  [Exact scope, earlier attempts and raw evidence](../../validation/qualification/2026-10-26-consumer-security/README.md).
- **S2** — unchanged security fixture at clean `5dd038138a9201f7f4423c5ebe8e1d604f4a4ae4`, tree `edcd6c30fc02fd1a2f1136fc9e4aec41889c2292`,
  default `6e85c4fe-3bc4-40b7-ae3e-db0893a8b9cc` PASS (222 JUnit / 116 Python), fresh external
  MVC 5 / WebFlux 6 cheap methods PASS. Oracle attempt `a85f571c-7cde-4b9e-979e-694155ce34b9`
  BLOCKED at 6.246–6.259 GiB host availability versus 12 GiB; both journeys NOT_RUN,
  wrapper exit 2, no resources created. [2026-10-28 evidence](../../validation/qualification/2026-10-28-consumer-security/README.md).
- **H** — historical results retain their original status and source; see
  [QM](../../QM.md) and the dated validation directories. They are not new execution.

DONE means the precisely scoped leaf is demonstrated. OPEN identifies the unmet
subcriterion. OUT OF FIRST RELEASE is a scope decision, not an implementation or
test PASS. Evidence listed beside an open row supports only the completed portion.

## Build, persistence and schema

| ID | Requirement / supported scope | Supporting source/run and demonstrated result | Current status / exact unmet subcriterion |
|---|---|---|---|
| R01.1 | Dependency-linked Java21/Boot4.1.1 build, architecture and cheap contracts | F default222 JUnit/102 Python; C default222 JUnit/112 Python and147-goal execution audit | DONE |
| R01.2 | Complete configured integration, mutation and semantic load | F83 Oracle methods/18 suites; PIT167 killed/168 total; six Gatling scenarios/367 requests/0 errors | DONE for `oracle26-nowait-ledger-first-v1`; no new full required merely to repeat PASS |
| R01.3 | Required CI checks, protected workflow/main and reviewed merge identity | F review evidence; remote default CI40324440091 PASS at report8d07aa7 | OPEN: administrative protection/review proof and eventual merge-source verification; merge is not authorized here |
| R02.1 | Two independent connections coalesce and reclaim with fencing | F OracleConcurrencyIT and OracleSelectionIT | DONE for measured independent-connection cases |
| R02.2 | Independent JVM crash checkpoints and fresh-process recovery | F all6 OracleRecoveryIT methods: after claim, owner response, projection flush, committed completion; newer generation/local revision controls | DONE for explicit headless-process fault points; OPEN: concurrent multi-process claim contention/continuous-worker pod-failure deployment qualification |
| R02.3 | JPA projection/JDBC ledger atomicity, rollback and physical commit rejection | F7 OracleAtomicityIT +7 NowaitOracleAtomicityIT, fresh committed state | DONE within single-datasource/qualified mapping scope |
| R02.4 | Reject wrong datasource enlistment | F default TransactionEnlistmentTest; actual runtime receipts | DONE for standard JpaTransactionManager; XA/separate ledgers are outside first release |
| R02.5 | Existing work/JPA contention, bounded LOCAL_CONTENTION, metadata failure | F7 OracleLockPolicyIT methods and qualified runtime policy receipts | DONE for accepted NOWAIT paths; arbitrary mappings/extra writes do not inherit this result |
| R02.6 | Shared quota acquire/cooldown contention and unrelated groups | F6 OracleQuotaContentionIT methods, independent starter instances | DONE for measured existing-row refusal/recovery |
| R02.7 | Legitimate first work/quota inserts and projection/state creation | F12 OracleFirstCreationIT cases; native Oracle26 work/quota INSERT refusal; work-fenced paired projection/state outcomes; DEFAULT controls retained | DONE for supported writers/configuration, including competitor commit and rollback |
| R02.8 | Bounded I/O, rollback, pool/read/owner/transaction and uncertain COMMIT observations | F11 mandatory OracleIoIT methods | DONE for controlled endpoint cases; OPEN: server lifecycle on unprotected supported waits, arbitrary network faults and operational resolution of uncertain outcomes |
| R02.9 | Plain-UPDATE independent client cancellation | Historical2c39b75/run31f8e5ee/report43040e7 FAILED; F current characterization NOT_RUN | Historical capability failure retained. Exact mechanism is outside named covered-path gate by accepted decision; no claim of cancellation PASS or universal bounded persistence |
| R03.1 | Fresh schema validation, v1→v2/index migration and cooldown extension1 | F OracleMigrationIT, runtime schema receipts | DONE for executed migration/data cases; no automatic production DDL |
| R03.2 | Migration interruption, pending/LEASED records, old-version rollback and backup restoration | F migration subset only | OPEN: stop/migrate/start rehearsal, interruption/restore, preserved indexes/constraints and pending-generation compatibility |

## Provider economics, runtime and operations

| ID | Requirement / supported scope | Supporting source/run and demonstrated result | Current status / exact unmet subcriterion |
|---|---|---|---|
| R04.1 | Oracle seeded capacity fixture, controlled physical calls/completions | F1M seeds/100k due;200 measured reconciliations/220 owner calls in15.624s;4 direct runOne workers | DONE as synthetic sample; OPEN: production-like capacity, sustainable provider/DB limits, peak-memory bounds and freshness/lag SLOs |
| R04.2 | Actual query plans, counters, selector and admission comparisons | F OracleScaleIT8 distributions and OracleOptimizationIT8 forward/reverse measurements | DONE for controlled comparisons; OPEN: sustained future-only/index-history/locked-prefix economics and multi-pod distributions |
| R04.3 | Complete claims/acquisition/owner/result accounting | F2916 claims/acquisitions=1766 grants+712 denials+438 observed refusals;1766 owner calls/1604 measured completions | DONE; historical unclassified55 remain unclassified |
| R04.4 | Durable cooldown publication, restart/recovery and shared pending admission barrier | F OracleCooldownIT +OracleCooldownWorkerIT and comparison:104 logical APPLIED,115 physical apply attempts/11 refusals,0 unresolved;32 lane runs ended normally | DONE for durably accepted receipts and measured recovery; historical five lost publications retain FAILED |
| R04.5 | Provider response before durable acceptance; acceptance failure/uncertainty and receipt maintenance | F explicit local-only unrecorded failure and uncertain outcomes | OPEN: an unrecorded response cannot be known by other nodes; define response-loss/uncertain-publication recovery, retention and mixed-version rollout. All sharing instances must consume DURABLE pending state |
| R04.6 | Actual Reactor pacing, bounded idle rechecks, urgent and recovery wakeup | F original OracleWorkerIT ten phases; fixed160/16.381s, adaptive160/9.904s;160/20s retained | DONE within small single-host budgets. Historical timeout remains FAILED/unexplained; no speculative redesign or criterion change |
| R04.7 | Correct cursor reload/reset/disappearance attribution | F raw endpoints/counters and retained cheap regressions; valid/unattributable distinction | DONE for attribution rules; no inferred wall-clock share or rewriting of old counters |
| R04.8 | Synthetic 429, latency, timeout and semantic load behavior | F Oracle workload plus six in-memory Gatling scenarios | DONE for synthetic fixtures; OPEN: real provider physical-call contract, transport retries/multiple calls, burst budget and starvation/SLO decisions |
| R04.9 | Owned process kills, controlled socket stalls/reset and shutdown during I/O | F OracleRecoveryIT/OracleIoIT with physical/resource observations | DONE for explicit faults; OPEN: whole-database loss, silent partitions/streaming peers and concurrent multi-pod failure |
| R05.1 | Bounded stop versus physical drain, late completion and fixture pool exhaustion | F OracleIoIT and retained worker unit tests; DRAINED/TIMED_OUT distinguished | DONE for controlled cases; OPEN: saturated-worker/shared-host pool/thread recovery |
| R05.2 | Operational metrics, liveness/readiness and alert validation | Test-only observations in F | OPEN: supported bounded-cardinality metrics, signals and forced-incident alert/runbook proof |
| R05.3 | Authorized DEAD inspection/replay and audit | Durable DEAD semantics in F | OPEN: authenticated operator workflow, reason/actor audit and tested recovery without ad hoc table edits |
| R05.4 | Second-operator incident exercise and deployment lifecycle | F test-owned cleanup | OPEN: deployment/backup/restore/rollback rehearsal, production resource configuration and operational approval |

## Security, distribution and maintenance

| ID | Requirement / supported scope | Supporting source/run and demonstrated result | Current status / exact unmet subcriterion |
|---|---|---|---|
| R06.1 | Tenant/binding/subject authority | F library contracts; S/S2 actual filter-chain/method checks and earlier Oracle WebFlux scoped read/refresh/event journeys | DONE for S/S2 cheap checks and S source-bound WebFlux scenarios. OPEN: final-source two-stack Oracle verification (S2 memory BLOCKED; both NOT_RUN), real identity-provider/policy/revocation/tenant lifecycle, other APIs/visits/replay and broader threat model. Raw gateway/event publisher are trusted in-process capabilities; qualifiers are not permissions |
| R06.2 | Provider transport/security configuration | F synthetic loopback fixtures | OPEN: TLS/secret rotation, endpoint/redirect/SSRF controls, payload limits/redaction and supported provider-specific review |
| R06.3 | Invalid identity/schema/authority fails closed | F library/schema checks; S/S2 cheap grant/scope checks; earlier S WebFlux 30 fresh-cache negative cases with unchanged protected side effects | DONE for declared F and executed S/S2 checks. OPEN: final-source two-stack Oracle verification (S2 NOT_RUN), production identity/host integration, arbitrary identifiers/mappings and complete security acceptance |
| R06.4 | Fresh reads versus a newly received event | Documented eventual freshness; F event generation/coalescing tests | OPEN: application acceptance of consistency limits; strict read-after-change is not promised |
| R10.1 | Parent/library artifacts with sources/Javadoc | C | DONE:12 POMs/11 main JARs/16 source and Javadoc attachments, recorded SHA-256; source-less starters have no Java attachments by profile design |
| R10.2 | Independent MVC/JPA and WebFlux/JPA local consumers | C, separate outside-reactor Maven invocations and isolated repositories | DONE:actual auto-configuration, effective policies, mixed rollback, clock replacement, disabled mode, WebFlux execution boundary and three committed phases per host; registry authentication remains R10.3 |
| R10.3 | Remote snapshot environment, authentication and publication/consumption | Existing manual guarded workflow/source documentation only | OPEN / NOT_RUN: environment protection, credentials/access and published registry consumer exercise. No remote publication authorized |
| R11.1 | Platform scope and API/version compatibility | F exact Java21/Boot4.1.1/Oracle23.26.3/Hibernate7.4.5/JDBC23.26.3;0.2 snapshot migration map | DONE for pinned named integration; OPEN: supported release-range policy, API compatibility/reproducibility and migration maintenance commitments. DEFAULT/older Oracle/arbitrary beans have no same bounded guarantee |
| R11.2 | Maven wrapper integrity | F Maven3.9.9 wrapper with checksum and Windows support | DONE; independent reproducibility/provenance review remains OPEN |
| R11.3 | SBOM, dependency/security/license triage | Pinned dependencies/build evidence only | OPEN: actual scans, triage, release provenance and supply-chain review |
| R11.4 | Stable release process / Maven Central namespace | No release/publication execution | OPEN: owned namespace, signing, protected immutable source/artifact release process if chosen; no version promotion authorized |
| R12.1 | Mapping/fingerprint evolution | F fixed single-table mappings | OPEN: mapping-version policy, invalidation/reconciliation and mixed-version rollout |
| R12.2 | Data/state/cooldown receipt retention and purge | Serving expiry and durable latest state are implemented | OPEN: separate retention policies, bounded purge with racing refresh/visit proof and authoritative-deletion policy |
| R12.3 | Audit history | MS_STATE latest result, immutable qualification reports | OPEN: application audit/history requirement and implementation; neither table is a full business-event audit ledger |
| R13.2 | Minimal documented public consumer setup | C templates and tested handbook setup | DONE:consumer-owned DTO/entity/mapping/binding and MVC/WebFlux controllers, documented tested setup; broader compatibility matrix remains R11 |

## Explicitly outside the first release

| ID | Deferred scope | Supporting decision / current result | Remaining work if later promised |
|---|---|---|---|
| R07 | Complete-population inventory/delta reconciliation | Owner-fixed known-ID scope; no importer qualification | Durable paging/cursors/snapshot coverage, crash boundaries/duplicates/tombstones and completeness manifests |
| R08 | Broker integration | Spring synchronous event adapter is tested; broker delivery not promised | Inbox identity/version, commit-before-ack, redelivery/poison handling and multicaster policy |
| R09 | IBM MDM, Vista/DWH and remote target delivery | No vendor connector or remote writeback in supported scope | Resolution/merges/splits, dataset/grain/cutoff agreement, delivery checkpoint/outbox and accepted/applied/visible outcomes |
| R13.1 | Batch/Integration/Modulith extension evaluation and BOM | Optional future adoption work, no measured runtime defect | Evaluate when an accepted capability requires it; no runtime rewrite is a first-release gate |

The [previous TODO at report8d07aa7](https://github.com/Tobia5/model-sync-starter/blob/8d07aa785be71a39ac82731606d3de950a61dd2e/TODO.md)'s repeated “obtain full,” fixture repair, acquisition accounting
and accepted cooldown implementation items are completed by F in their recorded
scope. Their failed/blocked attempts remain historical evidence, not duplicate
active tasks. The historical worker timeout is unresolved history/reproducibility
risk; this task neither reopens it nor assigns an unmeasured cause.
