# Model Sync qualification evidence

**2026-10-21. Default: PASS. Configured local full: PASS. Production: NO-GO.**

Tested code: **`ede199a33ad3a0ec5c911110bb84fd7407af1c25`**; tree: **`7c9577e9de49a2f96b1093a65673250a46163da6`**; clean change digest `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`. The later documentation/artifact commit is not claimed as full-tested source.

**First supported scope:** known-ID owner-to-Oracle/JPA reconciliation, one correctly enlisted datasource and `JpaTransactionManager`, with application-supplied detached mappings, owner reader, authorization and provider policy. Inventory, brokers and vendor connectors are future scope. All R01-R13 parent gates remain open.

## Commands and source identity

| Lane | Actual execution | Exit / result |
|---|---|---|
| Local default | `./mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify`; run `d0ef4b54-0ed3-428b-bbd8-1d1b77d62a54` | **0 / PASS**, 50.954 s; 103 JUnit tests / 20 XML, zero failures/errors/skips; 55 Python guards |
| Full | `python -u scripts/full-oracle-container.py`; run `2b75d9cc-8836-4e99-b227-9d836e7e207a`; attempt `c3a564fe-e255-4b85-a209-05244d9b86ec` | **Maven 0 / wrapper 0 / PASS**, 5623.33 s including provisioning/cleanup |
| Default CI | [39623846568](https://github.com/Tobia5/model-sync-starter/actions/runs/39623846568); run `b7c87459-3c64-4afb-9b50-630342f6ee05` | **PASS**, 103 JUnit tests; PR merge test SHA `5ac1a6d79b02dca796efd06aa973d5565aae5651`, identical tested tree |
| Offline contracts | Git Bash `bash scripts/test-triggers.sh`, sequentially after Maven | **0 / PASS**, 86 core / 43 trigger assertions; these are not additional JUnit tests |

Full used invocation-only `MODELSYNC_MAVEN_SETTINGS=.mvn/qualification-central.settings.xml`, `MODELSYNC_ORACLE_CONTAINER=EXPLICIT_DISPOSABLE`, and `MODELSYNC_FULL_ENV_ACK=DISPOSABLE_NONPRODUCTION`; all six external Oracle connection variables were cleared. The wrapper invoked `mvnw.cmd -B -ntp -s <repository>/.mvn/qualification-central.settings.xml -Pfull clean verify`. Project Central settings SHA-256: `ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`. No user/global Maven settings, DNS or TLS configuration changed.

Default recorded **102 plugin goals, none PIT/Gatling/Failsafe**. Architecture, docs, Python guards and declared/effective lifecycle checks passed. Full retained the base checks and produced **105 Surefire tests / 22 XML and 26 Oracle tests / 8 XML**, zero failures/errors/skips, all eight declared mutation classes, six Gatling simulations and the passing final evidence gate. No reports were rejected. No skip flags, reduced workloads or lowered thresholds were used. The separate default lane retains its own run marker and raw archive.

## Implementation and contract review

Separate indexed locking paths select due WAITING by numeric next-at and expired LEASED by numeric expiry. Recovery/waiting alternate within weighted priorities; eligible bindings rotate within priority. Equal-deadline order is unspecified. No Oracle pagination/locking combination is used. Client fetch size and maximum returned rows are not a database-work bound.

Non-consuming provider hints defer WAITING only. Expiring, token-checked instance reservations cover claim/prepare, recheck hints after acquisition, and release before owner latency. Expired-lease recovery remains eligible during cooldown. Distributed admission remains authoritative after the local planning transaction and before owner I/O. Fresh reads do not inspect quota. Custom stores can ignore advisory filtering while retaining correctness; their performance is not qualified by the JDBC measurements.

Fresh schemas include `MS_WORK_RECOVER_IX` and `MS_WORK_BIND_READY_IX`. Existing schemas require reviewed additive [queue migration 1](model-sync-persistence-jdbc/src/main/resources/model-sync/migration/oracle-queue-v1.sql) and [queue migration 2](model-sync-persistence-jdbc/src/main/resources/model-sync/migration/oracle-queue-v2.sql) before workers start. Startup verifies metadata without automatic DDL. `OracleMigrationIT` passed in 3.376 s, applying both indexes to an older-schema fixture, checking exact columns, retained data and refusal to replace an existing index. Leased/pending migration, interruption and restore gates remain open.

Responsibility review: `JdbcWorkStore` owns selection and fenced lease creation; `ProviderClaimScheduler` owns bounded advisory reservations; `SyncPlanner` preserves local planning/lock order and external admission; result application and retry policy retain their existing boundaries. No owner call runs under the local transaction. No production persistence is replaced by memory. This source/contract review and ArchUnit are not proof of every SOLID property or independent security QA.

## Environment and resource bounds

| Component | Actual version / bound |
|---|---|
| JDK / Maven / Python | Temurin OpenJDK 21.0.12.1 / Wrapper Maven 3.9.9 / Python 3.12.10, Windows |
| Boot / Spring / Hibernate / H2 | 4.1.1 / 7.0.9 / 7.4.5.Final / 2.4.240 |
| JUnit / Surefire-Failsafe / ArchUnit | Platform-Jupiter 6.0.3 / 3.5.3 / 1.5.0 |
| PIT / JUnit provider / Gatling | 1.25.9 / 1.2.3 / 3.13.1 (Maven plugin 4.11.0) |
| Oracle / JDBC | AI Database 26ai Free 23.26.3.0.0 / ojdbc11 23.26.3.0.0 |
| Container / effective Free DB | 6 GiB / 4 CPU cap; DB CPU count 2, SGA 1610612736 bytes, PGA 536870912 bytes, one writer, two 10 MiB redo groups |
| Workload JVM / workers / pool | Max heap 16944988160 bytes; end-used 180428696 bytes, not peak; 16 visible processors; 4 workers / 8 connections, lifetime retirement disabled only for stable test counters |

Pinned image: `ghcr.io/gvenzl/oracle-free:23.26.3-slim@sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`. Docker 29.8.2 Linux engine: 8 CPUs / 25201696768 bytes RAM. Preflight: 30.43 GiB free host RAM, C: 134.31 / D: 1070.18 GiB free, zero running containers; planned host budget Oracle 6 + build/PIT/Gatling 8 + reserve 4 GiB. No resource increase. Container samples around 2.0 GiB are observations, not a peak-memory qualification. Separate generated runtime/migration schemas and loopback port 54649 were used; no production connection or data.

## Controlled selector/admission comparison

100,000 all-due synthetic keys; one physical table/current index catalog, four workers/pool eight, owner spacing 20 ms, latency 5/25 ms, 429 every 17th entry and timeout every 29th. Each case warmed ten owner entries; 230-entry target, 150-completion floor and processing bound retained. Forward/reverse order, touched-state resets and gathered no-histogram statistics are recorded. Global caches were not flushed. Setup/statistics are outside measurement; measurement includes worker drain. Separate drain time is not instrumented. Seed 91075 ms / cleanup 118495 ms, zero remaining rows. This controlled catalog is separate from historical Q2 and the million-row sample.

| Variant / round | Measurement ms | Completed / owner entries | Claims / denials | No-lease polls | Selector callbacks | Logical gets / completed | Completed/s |
|---|---:|---:|---:|---:|---:|---:|---:|
| baseline / 0 | 38,456 | 201 / 222 | 323 / 101 | 0 | 873 | 163316.378 | 5.227 |
| selector-only / 0 | 8,255 | 200 / 220 | 836 / 616 | 0 | 4,100 | 510.025 | 24.228 |
| admission-only / 0 | 68,680 | 200 / 220 | 220 / 0 | 13,044 | 52,743 | 112698.435 | 2.912 |
| combined / 0 | 11,053 | 200 / 220 | 225 / 5 | 2,047 | 9,375 | 293.055 | 18.095 |
| combined / 1 | 11,026 | 200 / 220 | 223 / 3 | 2,002 | 9,191 | 287.605 | 18.139 |
| admission-only / 1 | 66,726 | 200 / 220 | 220 / 0 | 12,630 | 51,124 | 112663.505 | 2.997 |
| selector-only / 1 | 8,393 | 200 / 220 | 879 / 659 | 0 | 4,304 | 536.625 | 23.829 |
| baseline / 1 | 34,206 | 202 / 223 | 308 / 85 | 0 | 832 | 155011.000 | 5.905 |

**Tradeoffs remain visible:** selector-only was faster than combined but generated 616/659 denials versus 5/3. Admission-only had zero denials yet took 68.680/66.726 s, slower than baseline 38.456/34.206 s. Combined reduced expensive sorting/read work and denial writes; total selector executions can increase as polls return sooner. No-lease polls can contain quota-deferred work. Before seeding, twenty truly empty-queue polls per variant spent zero permits and made zero owner calls.

| Variant / round | Consistent / current gets | Physical reads | Sorted rows (whole phase) | Claimed latency p50/p95/p99 ms | Lag p50/p95/p99 ms |
|---|---:|---:|---:|---|---|
| baseline / 0 | 32,801,102 / 25,490 | 2 | 32,265,708 | 290/821/1288 | 23849/43131/44495 |
| selector-only / 0 | 42,085 / 59,920 | 1 | 58 | 21/56/59 | 9027/12894/13234 |
| admission-only / 0 | 22,522,506 / 17,181 | 0 | 21,975,968 | 306/350/399 | 39503/71677/74451 |
| combined / 0 | 40,131 / 18,480 | 0 | 64 | 41/57/58 | 8633/13863/14342 |
| combined / 1 | 39,378 / 18,143 | 0 | 64 | 41/57/59 | 8695/13907/14417 |
| admission-only / 1 | 22,515,257 / 17,444 | 0 | 21,975,971 | 291/336/447 | 37529/69789/72532 |
| selector-only / 1 | 44,728 / 62,597 | 0 | 64 | 20/56/58 | 7962/11832/12219 |
| baseline / 1 | 31,288,468 / 23,754 | 0 | 30,768,158 | 290/824/845 | 22254/39100/40216 |

| Variant / round | Log-file-sync waits, us | Resource-manager CPU waits, us | Native executions / parses | Redo bytes |
|---|---:|---:|---:|---:|
| baseline / 0 | 2,479,374 | 58,100,543 | 4,520 / 5,122 | 3,923,308 |
| selector-only / 0 | 7,604,199 | 0 | 11,840 / 12,440 | 9,089,720 |
| admission-only / 0 | 1,916,981 | 0 | 69,056 / 69,652 | 2,738,624 |
| combined / 0 | 1,586,228 | 0 | 14,724 / 15,324 | 2,742,844 |
| combined / 1 | 1,583,206 | 0 | 14,475 / 15,075 | 2,655,316 |
| admission-only / 1 | 2,126,616 | 0 | 67,010 / 67,610 | 2,680,940 |
| selector-only / 1 | 7,504,085 | 0 | 12,388 / 12,988 | 9,591,528 |
| baseline / 1 | 2,329,301 | 45,203,860 | 4,359 / 4,965 | 3,602,308 |

Native logical gets mean consistent + db-block gets; physical reads remain separate. Session deltas include observation overhead across a stable runtime-user pool. Sorted rows include observer SQL. Loaded-cursor before/after values and actual row sources remain raw. **Cursor reload continuity is ambiguous in one derived sum:** admission-only round 1 naively summed 103167 executions against 51124 counted selector callbacks and 67010 total native executions. Do not use that sum, cumulative cursor values or `window_executions` sums as an authoritative current-window count. Callback executions and stable-session deltas are the reported sources; a reviewed derivative retains this correction and the original derivative hash. Source collector ambiguity remains a tooling follow-up. Aggregate cursor/session time and logical-get share are not wall-clock share.

## Paired population probes and remaining empty-work cost

`OracleScaleIT`: PASS, 564.757 s; 100k seed 127480 ms / cleanup 140339 ms, zero rows remaining. Eight cases comprise five population mixes and three gathered-stat repeats. Each ran three rollback-only legacy probes then three selected probes in the same session/population/catalog; only the first selected all-due lease committed. Later probes rolled back. Legacy-first can warm caches. Statistics/setup costs and database-local analysis timestamps remain raw.

| Due / future / expired; statistics | Legacy logical gets (3 probes) | Selected logical gets (3 probes) | Legacy / selected claim-only ms (3 each) |
|---|---|---|---|
| 100000/0/0; all-due | 101775/101736/101736 | 42/45/34 | 238/221/219 / 4/2/1 |
| 10000/90000/0; due-future | 102788/102788/102788 | 39/35/38 | 86/83/88 / 2/1/2 |
| 0/90000/10000; expired-future | 104701/104495/104495 | 1769/23/1769 | 89/84/84 / 3/1/3 |
| 5000/90000/5000; mixed | 105544/105544/105544 | 23/36/23 | 89/82/87 / 1/1/1 |
| 0/100000/0; all-future | 106834/106834/106834 | 1951/1951/1951 | 73/70/75 / 3/2/2 |
| 100000/0/0; all-due-with-stats | 2317/2298/2298 | 262/38/246 | 144/138/142 / 4/1/2 |
| 5000/90000/5000; mixed-with-stats | 2310/2296/2296 | 49/31/38 | 24/23/23 / 2/2/1 |
| 0/100000/0; all-future-with-stats | 1718/1710/1710 | 1875/1859/1859 | 3/1/1 / 4/2/2 |

All 48 probes retained actual plans, separate counters and selector counts; physical reads were zero in these cached probes. Legacy plans 2896750126 / 2112819514 / 1175580303 differed with statistics. Selected ready/recovery plans 3325120528 / 949845695 used ordered range scans with no SORT; combined binding routing used plan 2126543754. Legacy all-due sorting consumed about 100k input rows per probe. Whole-probe counters include observation SQL; nonzero selected counts of 47-57 do not indicate a selector SORT.

**Improvement is not uniform:** gathered-stat all-future probes used 1859/1875 selected logical gets versus 1710/1718 legacy. Last empty ready scan used 1651 gets; recovery used 208 versus legacy lease-index 59, all returning zero rows. Retained/index-history causes are not isolated. This is not constant bounded database work, and JDBC max rows proves no such bound. Twenty locked-prefix records with later unlocked work, multiple workers, priority and repeated recovery passed eleven H2 and eleven Oracle regressions; arbitrarily large locked prefixes remain unqualified.

No claim batching or bulk-owner protocol was added or benchmarked. Selected productive claim samples took 1-4 ms here; empty probes cannot yield a claim batch. Future-only/index-history polling economics need investigation before broader sustained-scale claims. Resource/freshness SLOs and multi-pod fairness remain open.

## Million-row Oracle reconciliation workload

1,000,000 durable intents: 100k due / 900k future, uniform known IDs plus 100 coalesced hot-key requests; same workers/pool/provider timing/failures and target/floor. Real coordinator, JDBC ledger/quota and a **test-only JDBC projection**; not million-row JPA performance qualification.

| Phase | Actual result |
|---|---|
| Seed | 4336952 ms; 500 commits; 4013134664 redo bytes |
| Warmup | 848 ms; 10 owner entries / 10 persisted completions |
| Measurement | **11096 ms; 225 ledger claims; 220 grants / owner entries; five denials; 200 persisted completions** |
| Owned fixture teardown | **9045 ms; zero ledger rows**, restored/validated foreign key and rejected orphan insert |

Measurement throughput: **18.024513 completed syncs/s**, 19.826965 controlled owner entries/s. Total including warmup: 230 entries / 210 completed projections, 13 synthetic rate limits / seven timeouts. A million seeded rows are not a million completed synchronizations. Controlled stub entries do not prove real HTTP retry/fanout budgets.

There were 2205 polls / 1980 no-lease polls, 8549 store selections / 8324 empty selections, and 9055 completed selector callbacks. Claimed run-one p50/p95/p99 = **42/59/73 ms**; no-lease = 4/4/5; all polls = 4/42/58. These are worker operations, not visit latency. Lag = **4357199/4362505/4363016 ms**, including seed because requested-at predates setup. It is not steady-state lag or a freshness SLO.

Native measurement: **39464 consistent + 19509 current = 58973 logical gets**, 241 physical reads; 14370 executions / 14970 parses; 2920436 redo bytes; 2893 commits; 53 sorted rows. The legacy `db_session_work` 59175 includes physical reads and a different observation interval; do not force equality. Log-file-sync: 913 waits / 1838974 us; sequential reads: 241 / 13764 us; resource-manager CPU waits zero. Last waiting selector: one row, ten gets, one physical read, 144 us; last empty recovery: zero rows, three gets, seven us; neither sorted.

The owned teardown strategy is isolated to the generated, explicitly acknowledged loopback schema. A real one-record preflight passed in 2228 ms before seeding. Ten default regressions cover scope, unrelated rows/orphan state, ambient transactions, identifier/foreign-key checks, restoration failures and the pinned driver exception chain. The full gate requires matching preflight/teardown and restored/enforced FK evidence. External Oracle retains binding-scoped DELETE. **DDL teardown is not a row-DELETE performance comparison.** Q2 seed was 1899759 ms / 2257687880 redo bytes; current seed is slower and writes more. Historical host/cache and index catalogs differ, so causal attribution is not isolated. Seed/write economics remain a release gate.

## Retained transaction proof and mutation results

**`OracleAtomicityIT`: seven passed, 13.558 s**, unchanged case source from accepted Q2. Real starter auto-configuration, flushing JPA projection, JDBC ledger and configured `JpaTransactionManager`; fresh independent reads verified normal atomic success, rollback after flush, ledger constraint failure, Spring beforeCommit failure, physical Oracle COMMIT rejection, changed local revision and obsolete lease fencing. Separately committed retry metadata is allowed; partial successful projection/reconciliation commits are not. Six portable H2 cases and wrong-datasource startup rejection remain in default. This scope does not qualify XA, arbitrary routing or process-crash recovery.

**PIT 160/162 killed = 98.765432%**, eight seconds, unchanged **85% killed/all-generated** project gate. Raw counts: KILLED 160, SURVIVED 1, NO_COVERAGE 1; TIMED_OUT/RUN_ERROR/MEMORY_ERROR/NON_VIABLE zero. All eight declared classes actually generated mutations; original seven retained plus `ProviderClaimScheduler`; XML/HTML and scope/exclusion manifests preserved. PIT executed 418 internal test invocations; these are not extra Surefire tests.

All 22 scheduler mutations were killed. Prior non-equivalent dispatch mutants 72/75 are now killed by `unknownHintsRetainLegacyUnrestrictedDispatch` / `allKnownAvailableProvidersRetainExplicitBindingDispatch`. No exclusions were introduced. Remaining `positive(maxAge)` removal has a worker acceptance-equivalence argument: positive refreshAfter and refreshAfter <= maxAge reject nonpositive maxAge; null still fails comparison. Exception ordering/messages are not claimed equivalent. `SyncCoordinator.options` remains uncovered. Both remain in the denominator; maintainer review stays open. Java mutations do not qualify Oracle SQL.

## Gatling: in-memory semantic checks

**Six simulations PASS; 367 HTTP requests / zero errors; six passing assertion XMLs.** Local synthetic owner/harness through public APIs; no test endpoints shipped in starters and no remote target. Warmup zero; harness max heap 1 GiB, handlers eight/backlog 256. Fixed at-once users: 40 fresh, 100 hot, 20 unsafe, one event/cooldown, 200 leases. Rates below are short-run HTTP response rates, not completed-sync throughput or Oracle capacity.

| Scenario | HTTP requests / tool mean requests/s | HTTP p50/p95/p99 ms | Ledger rows / owner entries / completed rows | Harness measurement+drain ms |
|---|---|---|---|---:|
| fresh | 40 / 40.0 | 74/176/176 | 1 / 1 / 0 | 713 |
| hot | 100 / 100.0 | 195/202/203 | 1 / 1 / 0 | 699 |
| unsafe | 20 / 20.0 | 74/76/76 | 1 / 0 / 0 | 625 |
| event | 4 / 4.0 | 2/23/23 | 1 / 2 / 0 | 527 |
| cooldown | 3 / 3.0 | 5/25/25 | 1 / 2 / 0 | 1222 |
| leases | 200 / 200.0 | 279/290/294 | 200 / 80 / 80 | 790 |

Fresh visits made zero additional owner fetches; the one entry includes fixture initialization. Completed-row telemetry is shown as reported, not inferred from HTTP acceptance. The leases case completed 80 of 200 in-memory rows, not 200 syncs and not Oracle scale. Instrumented assertions cover fresh age/retention, coalescing, unsafe values, event/job/visit interaction, cooldown/recovery and leases. Hung real calls, network faults, real-provider retry budgets and sustainable SLOs remain unqualified.

## Preserved attempts, artifacts and cleanup

Immutable [4f44080 report](https://github.com/Tobia5/model-sync-starter/blob/a67966d16ccf6c140f4c95bebc2ddfd3ad497364/QM.md) and [raw ZIP](validation/qualification/2026-10-20/raw-evidence.zip): SHA-256 `ab7ff892a1d8ceafab878deb977d1eacb7becf53bdca5f3dab71ef06eebbf92c`. Immutable [1a9187c report](https://github.com/Tobia5/model-sync-starter/blob/e73b3deba59aab0401512aa73329b14b8adbcadd/QM.md) and [raw ZIP](validation/qualification/2026-10-21-atomicity-queue/raw-evidence.zip): SHA-256 `4805e6f2a1aa111cd280f29ccdcc06a106f1112d473b11ff08b470c8230343cd`. Both hashes reverified unchanged; their failures and accepted atomicity milestone remain intact.

| Optimization-stage attempt | Actual retained outcome |
|---|---|
| Sorted prototypes 00cc2d3 / 39b7b54 / 5bb5501 | Exploratory diagnostic results retained; sorts still processed the due population, so not selected as the final implementation. 39b7b54 attempt `b0481887-ff9d-47b5-ba5b-790242564da6` FAILED with ORA-12516; bounded eight-connection fixture pool fixed connection churn without resource increase. |
| Selected 2571703 diagnostic | Run `99615223-3748-4eeb-b1b8-c970dc670496`, attempt `79f18a7f-ba51-4242-9fea-e2698aebac66`, PASS selected eleven queue tests/eight comparison cases in 593.81 s; **full NOT_RUN**. |
| db31975 full | Run `956989c1-af81-4f1e-bc6e-3bb7f71ded81`, attempt `59032720-b5eb-4488-80e7-915887683fb9`, **FAILED/incomplete, intentionally interrupted**; Maven 1 / wrapper 1, 6967.33 s. 62 completed Surefire tests; OracleCapacityIT ran without final XML. Fresh partial: 200 completions / 220 entries / five denials in 11007 ms. DELETE cleanup reached 18.9% after 1330 s, estimated 5706 s remaining, beyond remaining supervised budget. Only verified own Failsafe JVM stopped. Later Oracle/JPA/Gatling NOT_RUN; not a product assertion failure or a timeout falsely called PASS. |
| Development failures | Source-changed H2 run `fcb1a328-3381-47e5-a6c7-b894bb28e13c` stays FAILED/unattributable; incorrect poll assertion and source edits during build are recorded. Dirty 20f54cd compile/test/report-guard failures remain. New helper compile run `876edca8-06ab-47d6-be0e-1bf7f066fb6c` FAILED (incorrect imports), then fixed. All dirty-run digests retained; no clean commit is credited with those results. |
| Auxiliary trigger attempt | Initial ede199a script exit 3: worker ran it alongside Maven clean, which removed its shared temporary source list. No product assertion ran. Sequential retry exit 0 / 86+43 assertions retained separately. |

Focused follow-ups [647f9d9](https://github.com/Tobia5/model-sync-starter/commit/647f9d9) (owned teardown) and [ede199a](https://github.com/Tobia5/model-sync-starter/commit/ede199a33ad3a0ec5c911110bb84fd7407af1c25) (dispatch contracts) were requalified above. The passing current run does not rewrite the earlier failed run. Its logical DELETE cleanup was unverified; its physical container/volume cleanup passed. Current container `msq-535b97413daf` and volume `msq-535b97413daf-data` are independently absent. Public image/dependency caches remain. No unrelated resources were stopped, removed or reconfigured.

[New raw evidence and manifest](validation/qualification/2026-10-21-queue-optimization/README.md): **3,774,804 bytes, 792 files, 21 distinct run IDs / 22 component archives**, SHA-256 **`e4c2878e674a9331dfda4d6dd89dd8bc97b8f5b5d42554869611323cfed5c4ff`**. Includes run-scoped QM/XML/HTML, plans/counters/waits, scope/versions/configuration, 11 library build-output hashes and failure/cleanup observations; no JARs. UTF-8/UTF-16/nested ZIP inputs were scanned for generated credential shapes and sensitive XML properties with zero findings. Raw snapshots are unmodified; exploratory/unbound observations and reviewed derivatives are classified separately from required suite evidence.

## Remaining release gates

**Production NO-GO.** Applicable work includes future-only/index-history/polling economics and ambiguous cursor reload accounting; independent-process/multi-pod crash/restart, racing inserts and cross-process quota registration/fairness; statement/lock/transaction bounds; interrupted leased/pending migrations and restore; sustainable provider/resource/freshness SLOs; observability/operator recovery; application tenant authorization/transport/privacy; independent consumers, compatibility/supply chain and retention/mapping evolution. All parent gates remain open. Inventory/broker/vendor gates remain unpromised future scope.

Full GitHub CI is **NOT_RUN**: accessible registration still lists only default verification. An owner-approved workflow-only bootstrap/protected environment is a separate prerequisite; main was not modified or PR #1 merged. This is not an external Oracle-secret blocker: the approved local disposable route completed. No package publication, merge or stable release was performed.
