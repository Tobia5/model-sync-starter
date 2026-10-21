# Model Sync qualification evidence

**2026-10-21. Default: PASS. Configured full qualification: PASS. Production: NO-GO.**

Tested source: **`1a9187ce0886912c14a2f77dbcccd3287a37e4a8`**; tree: **`0276124dd146d4fc9ae89dfe5ebddf7154840374`**. Working-change digest: `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` (clean). The later report/artifact commit is not claimed as the tested source. No production `src/main` files, claim SQL, indexes or schema changed from the preserved `4f44080` baseline; critical blob hashes and platform parity are in the raw archive.

First supported scope: **known-ID owner-to-Oracle/JPA reconciliation**, one correctly enlisted local datasource and `JpaTransactionManager`, with application-supplied owner reader, detached mappings and authorization. This work adds no inventory, broker or vendor connector feature. Applicable [release gates](TODO.md) remain open.

## Commands and results

| Execution | Actual command | Exit | Evidence |
|---|---|---:|---|
| Default | `./mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify` | 0 | Run `83d7f773-3c8e-4a21-91a8-d6b90453253f`; 49.649 s; 61 JUnit tests / 16 XML, zero failures/errors/skips; 52 Python guards |
| Full | `python -u scripts/full-oracle-container.py` with the process-only environment below | Maven 0 / wrapper 0 | Run `8a2204e2-1371-4cce-8b7a-7ee5c573ae14`; attempt `a47ddfb5-db5b-42f9-bb2e-7f2b86b79b2e`; 4,762.84 s including provisioning/cleanup |
| Core / triggers | Git Bash `bash scripts/test-triggers.sh` | 0 | 86 core / 43 trigger assertions; these are **not additional JUnit tests** |
| [Default CI](https://github.com/Tobia5/model-sync-starter/actions/runs/39518218060) | `./mvnw -B -ntp clean verify` | 0 | PR merge source `b6ddf286d17b488e54617aec8e733cd959489c2f`, identical `0276124…` tree; 61 JUnit tests |

Full's process-only settings were:

```text
MODELSYNC_MAVEN_SETTINGS=.mvn/qualification-central.settings.xml
MODELSYNC_ORACLE_CONTAINER=EXPLICIT_DISPOSABLE
MODELSYNC_FULL_ENV_ACK=DISPOSABLE_NONPRODUCTION
```

All six external Oracle connection variables were cleared before container mode. The wrapper generated credentials and passed the same reviewed settings file to its Maven subprocess: `mvnw.cmd -B -ntp -s <repository>/.mvn/qualification-central.settings.xml -Pfull clean verify`. Settings SHA-256: `ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`. User/global settings, DNS and TLS configuration were not changed.

Default's recorded execution contained **102 plugin goals, none PIT/Gatling/Failsafe**. Architecture, docs, declared/effective lifecycle and datasource/classpath guard checks passed. Full retained default checks and added the declared expensive work: **63 Surefire tests / 13 Oracle tests, zero failures/errors/skips**, nonzero PIT and all six Gatling simulations. The final evidence gate passed. Missing/filtered/skipped required suites cannot qualify.

## Environment and infrastructure

| Component | Actual version / bound |
|---|---|
| JDK / Maven / Python | Eclipse Adoptium 21.0.12.1 / Wrapper Maven 3.9.9 / Python 3.12.10 on Windows |
| Boot / Spring / Hibernate | 4.1.1 / 7.0.9 / 7.4.5.Final |
| JUnit / Surefire and Failsafe | Platform/Jupiter 6.0.3 / 3.5.3 |
| PIT / JUnit provider / ArchUnit | 1.25.9 / pitest-junit5-plugin 1.2.3 / 1.5.0 |
| Gatling / Maven plugin | 3.13.1 / 4.11.0, fixed noninteractive Java simulations |
| Oracle / driver | AI Database 26ai Free **23.26.3.0.0** / ojdbc11 23.26.3.0.0 |
| Oracle container | **6 GiB / 4 CPUs**, loopback-only port; Docker 29.8.2 Linux engine with 8 CPUs / about 25.2 GB |
| Effective Free database | 2 CPUs; SGA maximum 1,610,612,736 bytes; PGA target 536,870,912 bytes; one DB writer; two 10 MiB redo groups |
| Workload JVM / pool | Host JVM reports 16 processors, max heap 16,944,988,160 bytes; end-used 323,818,728 bytes (not peak); 4 workers / pool 8, test-only max lifetime 0 |

The pinned image was `ghcr.io/gvenzl/oracle-free:23.26.3-slim@sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`. Separate generated `MS_TEST_RUNTIME` / `MS_TEST_MIGRATION` schemas and synthetic data were used. Preflight recorded 31,378,165,760 bytes free host RAM, C: 159,028,957,184 / D: 1,149,127,507,968 bytes free, and zero running containers. A cleanup-time container sample used 1.98 GiB / 6 GiB; it is not a peak-memory qualification. No resource increase or production connection was used.

## Mixed JDBC/JPA transaction proof

**`OracleAtomicityIT`: 7 passed, 11.95 s**, through actual starter auto-configuration, `JpaTransactionManager`, the real flushing `JpaProjectionStore` and `JdbcWorkStore`. Fresh independent DriverManager connections/read transactions bypassed JPA caches and inspected committed projection/version, validator/outcome and work status/token/generation/attempts.

| Case | Verified committed outcome |
|---|---|
| Successful completion | Projection and APPLIED reconciliation commit together; lease cleared |
| Failure after real projection flush | Original successful projection/state and original lease retained |
| Constraint error during JDBC completion | JPA flush and ledger/state writes roll back together |
| Spring `beforeCommit` failure | Both uncommitted writes observed before injection; both roll back |
| Physical Oracle COMMIT rejection | Two deferred-unique inserts accepted, then COMMIT rejects `MS_TEST_ATOMIC_UQ`; both successful writes roll back and fault rows are absent |
| Changed local revision | Local change retained; owner evidence not advanced; LOCAL_CONFLICT rescheduled |
| Obsolete/reclaimed lease | Old completion throws LeaseLost; original projection/state and replacement token retained |

Separately committed WAITING/attempt-1 retry metadata is permitted after rollback; it does not commit the failed successful result. The default build also passed six portable H2 cases and the negative startup test rejecting a different transaction-manager datasource **before schema access**. These prove the stated single-datasource configuration, not XA, arbitrary routing configurations, racing inserts or pod-crash recovery.

The first full attempt reaching this suite failed at startup because Failsafe selected the sample's repackaged Boot JAR. The focused fix sets full-profile `classesDirectory` to compiled output, retaining Boot packaging and all platform versions/suites. The cheap audit resolves the sample's **effective** POM and rejects missing/JAR-based classpaths; the passing Oracle Failsafe XML confirms actual compiled application output at runtime. This follows [Boot's integration-test guidance](https://docs.spring.io/spring-boot/maven-plugin/integration-tests.html).

Human contract review: the fault seam wraps the real projection capability; it neither copies managed entities reflectively nor substitutes memory persistence. Production transaction/lock order and owner authority are unchanged. Fresh-connection assertions detect partial commits; lease and local-version tests protect the application boundary. Architecture tests alone are not a proof of every SOLID principle.

## Oracle queue measurements

The million-row workload uses the real coordinator, JDBC ledger/quota and a **test-only JDBC projection**; it is not a million-row JPA performance benchmark. Dataset: **1,000,000 known IDs, 100,000 due / 900,000 future**, uniform synthetic keys plus 100 coalesced hot-key requests. Four workers/pool eight; owner spacing 20 ms, latency 5/25 ms, synthetic 429 every 17th entry and timeout every 29th. Bounds and assertions stayed unchanged: 230 owner-entry target, 150 persisted-completion floor, three-minute processing deadline. The floor proves a completed sample, not a production throughput SLO.

| Phase | Actual duration / outcome |
|---|---|
| Seed | 1,899,759 ms; 1,000,000 rows; 500 commits; 2,257,687,880 redo bytes |
| Warmup | 3,457 ms; 10 owner entries / 10 persisted completions |
| Measurement | **43,861 ms; 360 ledger attempts; 222 quota grants / owner entries; 138 denials; 201 completed reconciliations** |
| Cleanup | 2,002,711 ms; **zero rows remaining**; 1,595,517,164 redo bytes |

**Total: 211 persisted completions from 232 controlled owner-stub entries**, including warmup; 13 synthetic 429s and eight timeouts. Measurement: **4.582659 completed syncs/s**, 5.061444 owner entries/s. A million-row seed is **not** a million completed synchronizations. Ledger claims and quota admissions are distinct. Stub entries are not evidence of real-provider HTTP retry/fanout budgets.

Measurement native counters: **36,565,639 consistent gets; 18,804 db block gets; 9 physical reads; 4,945 executions; 5,547 parses; 2,843,936 redo bytes; 1,453 commits**. The retained legacy sum is 36,584,415: **101,623.375 per ledger attempt**, 164,794.662 per owner entry, 182,012.015 per completed sync. Its physical-read term is not a distinct logical-work unit; native counters remain separate. The separate snapshot's logical-get total is 36,584,443; the legacy sum uses a slightly different observation interval and includes physical reads. They must not be forced into arithmetic equality.

Actual selector SQL `9pa35qd17fnm3`, plan **1899688225**, used ready-index OR concatenation, batched rowid access and a sort over about 100k eligible rows to return one lease. Measurement V$SQL delta: **972 executions / 360 returned rows / 36,558,102 buffer gets / zero disk reads**; 88.273435 s CPU and 151.733604 s elapsed aggregated across concurrent executions. About **99.93% of measured logical gets** belonged to this selector. The 138 denials (38.33% of attempts) repeated a costly claim; quota SQL itself was comparatively small. These data do not allocate traced CPU separately to each denial.

Run-one p50/p95/p99: **297/861/894 ms**. Reconciliation lag: **1,928,848/1,949,099/1,950,658 ms**, across all persisted successes including warmup and the long seed. These are neither visit latencies nor an agreed freshness SLO. Measured CPU-quantum waits totalled 70.754717 s; log-sync 3.558652 s. Cleanup checkpoint-incomplete waits totalled 1,691.087446 s, plus 273.208704 s in Oracle's generic Other bucket. Current-session samples also observed block-range reuse checkpoint waits; the entire Other bucket is not attributed to that event. Waits/SQL elapsed aggregate and overlap; they are not wall time. One optional SYS-view observer hit its 20-second bound; required native suite reports completed.

### Before/after under unchanged declared limits and workload

| Metric | Preserved `4f44080` PASS | `1a1905c` partial, full FAILED | `1a9187c` full PASS |
|---|---:|---:|---:|
| Measured attempts | 337 | 397 | 360 |
| Measured owner entries / completions | 222 / 201 | 223 / 202 | 222 / 201 |
| Processing ms | 45,417 | 46,908 | 43,861 |
| Legacy work / attempt | 101,595.932 | 101,597.035 | 101,623.375 |
| Measured quota denials | Not separately instrumented | 174 | 138 |

Production query/index/schema and container limits are unchanged. Cache, checkpoint debt, table history, instrumentation and host scheduling differ. These observations identify selector cost and denial churn; they do **not** establish an optimization or sustainable production capacity.

### 100k due/future/expired-lease distributions

Three samples per distribution; all rows use exact production `WorkKey.storageId()`. The first lease was committed and checked for LEASED status, token, generation and `lockValid()`. Later samples roll back. Population setup, statistics gathering and cleanup have separate time/counter phases. Table statistics after gathering: 100,000 rows / 24,377 blocks; database-local LAST_ANALYZED is recorded without inventing a UTC offset.

| Population (due / future / expired) | Plan hash | Consistent gets, three samples | Claim-only ms, three samples |
|---|---:|---|---|
| 100k / 0 / 0 | 2044803774 | 6855 / 6639 / 6639 | 209 / 179 / 185 |
| 10k / 90k / 0 | 2044803774 | 6640 / 6639 / 6639 | 60 / 60 / 59 |
| 0 / 90k / 10k | 2044803774 | 7468 / 7467 / 7467 | 62 / 60 / 61 |
| 5k / 90k / 5k | 2044803774 | 7467 / 7467 / 7467 | 83 / 63 / 62 |
| 0 / 100k / 0 | 2044803774 | 7464 / 7464 / 7464 | 47 / 44 / 46 |
| 100k / 0 / 0, gathered stats | 1059636482 | 7590 / 7488 / 7488 | 212 / 180 / 180 |
| 5k / 90k / 5k, gathered stats | 3121454005 | 7483 / 7469 / 7469 | 44 / 44 / 42 |
| 0 / 100k / 0, gathered stats | 1175580303 | 1778 / 1770 / 1770 | 3 / 2 / 1 |

All **24 samples** retained actual A-Rows plans. The first claim was **6,871 legacy units / 237 ms including checks/commit**; seed 151,169 ms and cleanup 151,128 ms, zero rows remaining. B1 recorded 132,950 / 7,411 ms without an actual plan and with different connection lifetimes; this is not a controlled SQL-optimization comparison. The previous run reused ready-index plan 1899688225 before gathering; this run selected bitmap lease-index plan 2044803774. Gathered-stat plans included lease-index skip scan 1059636482 and OR paths 3121454005/1175580303. Statistics did not uniformly improve all latency/read metrics. A fresh 100k diagnostic also had a different table layout from this post-million-row fixture.

Cursor evidence uses SQL ID, child number/address, load time/count and positive **window executions**. Actual Oracle reused a child/address while resetting execution counts; the fixed collector retained the reloaded plan. Cumulative cursor values are not attributed wholesale to a later distribution. No production optimization was made: a bounded selection/admission redesign would need separate fencing, fairness, recovery and provider-budget proof.

## Mutation and semantic load

PIT **PASS: 133 KILLED / 135 generated = 98.518519%**, required **85% killed/all generated**, not test strength. Counts: 1 SURVIVED, 1 NO_COVERAGE, 0 TIMED_OUT, 0 RUN_ERROR, 0 UNKNOWN. PIT reported six seconds total. Scope is the unchanged seven behavioral classes in `model-sync-testkit/pom.xml`; scope/exclusion manifests and XML/HTML are archived. No broad exclusions or threshold reductions were used. Removing `positive(maxAge)` is explained as equivalent under the remaining positive refreshAfter and refreshAfter <= maxAge constraints; the uncovered `SyncCoordinator.options` accessor remains in the denominator. Worker explanations are retained; maintainer review remains pending. Java mutations do not qualify Oracle SQL.

The six **in-memory semantic** Gatling simulations passed, **367 requests / zero HTTP errors / six passing assertion XMLs**. They are separate from Oracle capacity evidence; HTTP acceptance is not reconciliation completion. No remote load target/provider was used.

| Scenario | At-once users / ledger rows | HTTP requests | Tool mean HTTP requests/s | HTTP p50/p95/p99 ms | Harness measurement/drain ms |
|---|---|---:|---:|---|---:|
| Fresh visits | 40 / 1 | 40 | 40 | 86 / 195 / 196 | 748 |
| Hot stale key | 100 / 1 | 100 | 100 | 195 / 201 / 202 | 642 |
| Unsafe values | 20 / 1 | 20 | 20 | 62 / 64 / 64 | 553 |
| Event/job/visit | 1 / 1 | 4 | 4 | 3 / 21 / 21 | 419 |
| Cooldown/recovery | 1 / 1 | 3 | 3 | 5 / 27 / 27 | 709 |
| Leases/ledger | 200 / 200 | 200 | 200 | 300 / 324 / 326 | 1089 |

Semantic harness warmup is zero, JVM max heap 1 GiB, handlers eight and accept backlog 256 for the largest 200-user burst. HTTP rates are Gatling's short-run accepted-response rates, not completed-sync throughput or a capacity SLO. Fresh-cache assertions prove **zero additional owner fetches** during visits; the summary's one total owner entry includes fixture initialization. The 200-row in-memory leases scenario recorded 71 completed rows, not 200 completed syncs and not Oracle scale. Error assertions and instrumented ledger/projection outcomes remain required; no arbitrary production SLO is inferred.

## Preserved history and artifacts

**Immutable B1:** source `4f440807f20c568a2240ed5ce32f1aab8e9558b0`, tree `02faa6e6b9b5b8a28af6234d2b1de699a736b735`; full run `3177a6c3-3b64-4e5b-b40b-dc18bf1c7884`, attempt `99a77d95-2499-4964-95d9-8e4ecf67d84d`, PASS. [Pinned report](https://github.com/Tobia5/model-sync-starter/blob/a67966d16ccf6c140f4c95bebc2ddfd3ad497364/QM.md); [unchanged raw ZIP](validation/qualification/2026-10-20/raw-evidence.zip), 1,928,952 bytes, SHA-256 `ab7ff892a1d8ceafab878deb977d1eacb7becf53bdca5f3dab71ef06eebbf92c`. Its earlier blocked/failed attempts remain there.

| Later attempt | Actual result retained |
|---|---|
| `dc5daf4`, run `a9ec56c4-f102-4f13-8786-a3c9dfae625a`, attempt `1acee4c8-f84d-4dd9-8435-ea3fb827863b` | **FAILED / intentionally interrupted**, Maven 1 / wrapper 1, 3,622.17 s. Read-only probe confirmed ORA-00979 in pending diagnostic SQL; only owned container was stopped during cleanup. Six subsequent Oracle errors are retained as interruption effects, not product regression claims. JPA Oracle/Gatling did not run. |
| `95f975f`, run `f09fde00-1dc7-42f1-b2ce-a188401e6ee1`, attempt `9db520ad-05ac-4533-b6a8-e032e9c945be` | **FAILED**, Maven 1 / wrapper 1, 4,483.02 s. 31 Surefire tests; 6 Oracle tests with one plan-collector error after cursor reload. JPA Oracle/Gatling did not run. |
| `1a1905c`, run `adee2845-ab48-42b5-87a3-5777684df11a`, attempt `40c7fce8-d1d6-40d0-aef2-96a792a7aac4` | **FAILED**, Maven 1 / wrapper 1, 4,229.05 s. 59 Surefire tests; all 6 JDBC Oracle tests passed, followed by one Oracle JPA class-startup error (Boot JAR classpath). Gatling did not run; stale leftovers were rejected. |
| Separate `1a1905c` diagnostic, run `7cb9e0f2-33d2-4bc9-9deb-28e398ee5b5d` | **PASS only for selected diagnostics**, 420.83 s: 7 Oracle atomicity cases plus 1 scale test. Full **NOT_RUN**; full gate rejected insufficient/stale remaining evidence. Not substituted for the final run. |

Each attempt keeps its own source/tree/run identity, original exits and fresh partial reports. All owned containers/volumes from these attempts were removed and independently checked. The final container `msq-95ecb43fc078` and volume `msq-95ecb43fc078-data` are absent. Public image/Maven dependency caches are retained; no unrelated resource was stopped or removed.

[New raw archive and manifest](validation/qualification/2026-10-21-atomicity-queue/README.md): **2,823,792 bytes, 706 files / 13 run records**, SHA-256 **`4805e6f2a1aa111cd280f29ccdcc06a106f1112d473b11ff08b470c8230343cd`**. It includes run-scoped `target/qm/QM.md`, XML/HTML, native plans/counters, configuration/cleanup evidence and 11 library build-output hashes. It contains no published JARs. UTF-8/UTF-16 and nested report ZIPs were scanned for generated credentials/sensitive system properties before publication.

## Remaining release gates

**Production NO-GO.** Parent R01-R13 gates remain open. Applicable work includes independent-process/multi-pod crash/restart and racing-insert proof; quota registration races/bulkheads and real HTTP fanout; statement/lock/transaction timeouts; leased/pending migration and backup restore; sustainable production capacity/freshness/resource SLOs; observability and operator recovery; tenant authorization/transport/privacy; independent package consumers/compatibility/supply-chain review; mapping evolution and retention. Million-row JPA performance and production-like statistics/distributions are not qualified by this JDBC sample. Inventory/broker/vendor gates remain unpromised future scope.

Full GitHub CI is **NOT_RUN**: the accessible Actions registry lists only default verification; the full workflow is present on the PR branch but not registered on main. An owner-approved workflow-only bootstrap/protected environment is a separate prerequisite for that CI route. Main was not modified, no environment approval was bypassed, and this is not an external-Oracle-secret blocker: the explicitly authorized local container route completed. No merge, package publication or stable release was performed.
