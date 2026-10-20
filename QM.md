# Model Sync quality evidence

## Result and tested identity

| Lane | Result | Evidence |
|---|---|---|
| Local default build | **PASS**, exit **0** | 50 JUnit tests, 13 XML files; 0 failures/errors/skips; 44.300 s |
| Default PR CI | **PASS**, exit **0** | Same source tree; 50 JUnit tests, 0 failures/errors/skips |
| Approved local `full` qualification | **PASS**, Maven **0**, wrapper **0** | 52 Surefire + 6 Oracle tests; 6 Gatling scenarios; PIT 98.52%; required raw-evidence gate passed |
| Full GitHub workflow | **NOT_RUN** | Workflow is unregistered on the default branch; see setup below |
| Production readiness | **NO-GO** | Applicable release gates remain open; see limitations below |

**Tested source:** `4f440807f20c568a2240ed5ce32f1aab8e9558b0`. **Tree:** `02faa6e6b9b5b8a28af6234d2b1de699a736b735`. Both local builds ran with a clean working tree (change digest `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`). This later documentation/artifact commit does not claim that its changed tree was tested with `full`.

- Local default run: `f50960a1-2cec-438d-90ca-3ea6d70cdc30`.
- Local full run: `3177a6c3-3b64-4e5b-b40b-dc18bf1c7884`; container attempt: `99a77d95-2499-4964-95d9-8e4ecf67d84d`.
- [Default CI run 39452003862](https://github.com/Tobia5/model-sync-starter/actions/runs/39452003862) tested merge commit `410a7fc4d52e3ab7281b53a3c2a51e486cd17942`, with the **same tree**. Its run identity is `16349026-5968-401b-9978-65df4e632aa3`; [CI QM/raw artifact](https://github.com/Tobia5/model-sync-starter/actions/runs/39452003862/artifacts/12279521849).

## Commands, configuration and environment

Working directory: repository root. The user authorized project-scoped public Maven Central resolution after the user-level `kubernetes-dev` mirror failed DNS resolution. The reviewed `.mvn/qualification-central.settings.xml` was used by both local builds and the wrapper's Maven subprocess. Its SHA-256 is `ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`. User/global Maven settings, DNS and TLS settings were not edited.

```powershell
$env:MODELSYNC_MAVEN_SETTINGS='.mvn/qualification-central.settings.xml'
.\mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify
# exit 0

# These values were set only in the qualification subprocess environment.
$env:MODELSYNC_ORACLE_CONTAINER='EXPLICIT_DISPOSABLE'
$env:MODELSYNC_FULL_ENV_ACK='DISPOSABLE_NONPRODUCTION'
python -u scripts/full-oracle-container.py
# Maven exit 0; wrapper exit 0
```

External Oracle connection variables were cleared for this invocation; the wrapper generated credentials and separate `MS_TEST_RUNTIME` / `MS_TEST_MIGRATION` schemas in a fresh database. It invoked `mvnw.cmd -B -ntp -s <absolute reviewed project settings> -Pfull clean verify`. Maven reported `01:49 h`; the wrapper measured **6638.75 s**, including provisioning and cleanup. The ordinary default execution audit found **102 plugin goals, none PIT/Gatling/Failsafe**. Full retained the default checks.

| Component | Actual version / resources |
|---|---|
| Local host | Windows 11 Home 10.0.26300; Temurin 21.0.12.1+1; Python 3.12.10 |
| Maven / platform | Wrapper Maven 3.9.9; Boot 4.1.1; JUnit Jupiter/Platform 6.0.3 |
| Test tools | Surefire/Failsafe 3.5.3; ArchUnit 1.5.0; PIT 1.25.9 + JUnit plugin 1.2.3; Gatling 3.13.1 + Maven plugin 4.11.0 |
| Oracle | Oracle AI Database 26ai Free, Release/Version 23.26.3.0.0; `ojdbc11` 23.26.3.0.0 |
| Docker | Client/server 29.8.2; 8 CPUs; 25,201,696,768 bytes available to Docker |
| Preflight free resources | 24.70 GiB host RAM; C: 138.52 GiB; D: 1070.24 GiB; zero running/retained qualification containers or volumes |
| Oracle limits | **6 GiB / 4 CPUs**, unchanged; fresh labelled volume; loopback-only `127.0.0.1:60372` |
| Workload JVM / pool | Max heap 16,944,988,160 bytes; measured-end used heap 326,143,992 bytes (not peak); 16 visible processors; pool capped at 8 |
| Local load host | 8 handler threads; accept backlog 256 for a maximum 200-client burst; Gatling fork max heap 1 GiB |

Pinned image: `ghcr.io/gvenzl/oracle-free:23.26.3-slim@sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`. No existing database, production credential, customer data or remote owner was used.

## Checks and mutation evidence

The complete default and full Maven builds passed compilation and dependency-linked tests. Full recorded **52 Surefire tests in 15 XML files**, plus **6 Failsafe tests in 5 XML files**, all with **0 failures, 0 errors and 0 skipped**. This includes the full-only architecture scan and the new 200-socket host regression. Compiled-output ArchUnit and source/POM guards passed. Python guards passed **46 tests**, including failed PIT/Oracle collection, stale/missing/malformed evidence, blocked preflight after prior success, and failed HTTP assertions despite passing harness semantics. Git Bash contracts passed **86 core / 43 trigger assertions**; syntax parsing checked **166 Java files, 0 errors**. These assertion/Python/syntax counts are separate from JUnit counts.

PIT executed successfully with the resolved JUnit Platform 6.0.3; no production platform downgrade was used. Runtime: **10 s**. Project gate: **at least 85% KILLED / all generated**, nonzero mutations, and no timeout/run-error/unknown result. Actual: **135 generated; 133 KILLED; 1 SURVIVED; 1 NO_COVERAGE; 0 TIMED_OUT; 0 RUN_ERROR; 0 UNKNOWN = 98.52%**. All seven declared classes generated mutations; scope and threshold were retained.

Scope: `io.modelsync.core.domain.FreshnessPolicy`; application `SyncAccess`, `SyncCoordinator`, `SyncPlanner`, `SyncResultApplier`, `SyncFailurePolicy`, `SyncJob`. Target tests: `CoreContractsTest`, `TriggerContractsTest`. Declared class/method exclusions: **none**. [Scope, per-class counts and review manifest](validation/qualification/2026-10-20/mutation-review.json).

Worker review of the two remaining results, retained in the denominator:

- **SURVIVED:** removal of `positive(maxAge)` in `FreshnessPolicy` line 19 is equivalent for accepted values and exception types under the remaining positive `refreshAfter` and `refreshAfter <= maxAge` constraints. Diagnostic message/stack location can differ. Nonpositive maximum age still fails; null still raises NPE during comparison. No exclusion or killed credit was applied. Maintainer review remains part of PR review.
- **NO_COVERAGE:** null return from the direct `SyncCoordinator.options()` accessor, line 91. Selected behavioral tests do not call this accessor. It remains uncovered; no getter-only score padding was added.

PIT does not qualify Oracle SQL or all project behavior. The layer/responsibility review is in [LAYERED-REFACTOR](docs/quality/LAYERED-REFACTOR.md); structural rules are not a proof of every SOLID property.

## Real Oracle results and scope

All five required classes passed: capacity **1** test / **5998.447 s**; concurrency **2** / **0.772 s**; migration **1** / **3.747 s**; scale **1** / **475.741 s**; smoke **1** / **0.588 s**. Concurrency covers competing connections/coalescing and expired-token rejection. Migration covers v1-to-v2 WAITING/DEAD data preservation and IDLE support in the separate migration schema. These are the implemented cases, not the complete R02/R03 production gate.

`OracleScaleIT` seeded **100,000 due rows** with exact production `WorkKey.storageId()` values. It verified persisted LEASED status, token, generation and `lockValid()`. One **ledger claim** measured **132,950** session block-work units and **7411 ms** including lease assertions/commit; seed time **232,005 ms**. A ledger claim is not a provider admission. The default H2 regressions cover inconsistent IDs / zero-row claims, and `JdbcWorkStore.claim` requires exactly one updated row.

### Million-record workload

| Measurement | Actual result |
|---|---|
| Synthetic ledger / distribution | **1,000,000** intents; **100,000 due**, **900,000 future-scheduled**; numeric IDs; 100 explicit hot requests coalesced on one key |
| Execution | 4 worker threads in one JVM; pool maximum 8; 3-minute measurement deadline; owner-call target 230; completion floor 150 |
| Controlled owner stub | Shared 20 ms spacing (50/s cap); 5/25 ms latency; 429 every 17th call, timeout every 29th |
| Seed / warmup | **2,556,749 ms** seed; **3812 ms** warmup; 10 stub calls / 10 completed syncs |
| Measurement and drain | **45,417 ms**; 337 ledger attempts; **222** measured stub calls; **201** measured completed syncs |
| Total persisted result | **211 completed syncs = 211 projection rows**, from **232** total stub calls |
| Measured throughput | **4.42566 completed syncs/s**; **4.88804 stub calls/s** |
| Injected failures | 13 rate limits + 8 synthetic timeouts; 9.0517% of total stub calls; these are expected injections, not PIT timeouts |
| Database work | **34,237,829** summed session `db block gets + consistent gets + physical reads`; **154,224.455** per measured stub call |
| Run-one latency p50 / p95 / p99 | **351 / 899 / 927 ms**; includes ledger/quota work, not HTTP visit latency |
| Reconciliation lag p50 / p95 / p99 | **2,584,058 / 2,607,095 / 2,609,753 ms**; includes the roughly 42.6-minute seed phase |

The 150-completion floor checks a bounded nonempty sample; it is not a production SLO. Future-scheduled intents are not verified fresh projections. One million seeded rows are **not one million completed refreshes**. The metric named `physical_owner_calls` counts controlled stub fetch entries, with no real owner HTTP or adapter-internal retry/MDM/bulk fanout. The database counter sum is not a SQL-query count or distinct physical-block count. Pool lifetime retirement was disabled only for this bounded test to keep session-counter populations stable; all eight connections closed at test exit. Long seed/cleanup time is retained in runtime evidence, not presented as sync throughput.

## Gatling: local in-memory semantics

All six explicitly selected simulations passed their HTTP and owner/ledger assertions: **367 requests, 0 failed (0%)**, six nonempty assertion reports. There was no separate load warmup; fresh/hot/event fixture setup performs an initial owner fetch. Host lifecycle durations (601-910 ms) include fixture setup/drain; Gatling's short-burst request rates below are not capacity SLOs. Owner counts include fixture setup. No remote target or production persistence is claimed for this harness.

| Scenario | HTTP requests | Failed | HTTP p50 / p95 / p99 ms | Gatling req/s | Stub calls |
|---|---:|---:|---|---:|---:|
| Fresh cache | 40 | 0 | 70 / 164 / 169 | 40 | 1 |
| Hot stale key | 100 | 0 | 179 / 186 / 187 | 100 | 1 |
| Unverified value | 20 | 0 | 65 / 67 / 67 | 20 | 0 |
| Event / visit / finish | 4 | 0 | 2 / 21 / 21 | 4 | 2 |
| Cooldown / recovery | 3 | 0 | 5 / 25 / 25 | 3 | 2 |
| Concurrent jobs | 200 | 0 | 257 / 270 / 272 | 200 | 80 |

Fresh visits caused **zero additional fetches** after setup and preserved validation age; hot visits coalesced one ledger item without a stampede; unverified values stayed pending. The event case retained urgent follow-up work during an in-flight fetch; cooldown recovered to matched state. The concurrent-job case had **200 accepted job requests, 80 owner calls and 80 completed ledger rows**, not 200 completed syncs. Completion counters for that tracked-ledger case are distinct from HTTP acceptance. Other semantic outcomes are asserted directly on their fixture state. Real Oracle capacity is the separately labelled workload above.

## Failed attempts retained separately

All attempts used their own source/run identity. The artifact includes original reports/logs and the recorded collection correction for the interrupted run. No earlier report was reassigned to the successful run.

| Attempt / source | Final outcome | Executed evidence / concrete reason |
|---|---|---|
| `2f9a0534` / `4bd4cef` | **BLOCKED**, wrapper 2; Maven NOT_RUN | Unsupported Docker info template field; no container created |
| `6f5bce39` / `104891a` | **BLOCKED**, wrapper 2; Maven NOT_RUN | Windows temporary SQL-file bind mount access failure; resource absence verified |
| `a8ca1821` / `a5ca45d` | **FAILED**, wrapper 1; intentionally interrupted | Earlier storage/cleanup attempt stopped only its owned container; PIT 115/135 (85.19%); million-row seed and 211 projections measured; six Oracle errors after stop; Gatling NOT_RUN. Corrected collector retains measured failure counts; this is not a product-regression claim |
| `2c19130d` / `1cc449d` | **FAILED**, Maven 1 / wrapper 1 | 51 Surefire and six Oracle tests passed; PIT 98.52%; fresh Gatling 17/40 OK, **23 refused**, 57.5% failure; five later simulations NOT_RUN; cleanup verified |
| `99a77d95` / `4f44080` | **PASS**, Maven 0 / wrapper 0 | Current complete result above; raw manifests admit 234 files with **zero rejected** |

The `1cc449d443b1fd892a5c5de4f86b7f15edbdd794` failed run has tree `eef498799d3861b76506bee93b779c6839a1df37`, evidence run `2547dd26-2d90-4b78-98c8-3a120785ca5b`. Its mislocated root harness JSON is retained as failed-run diagnostics. Focused commit `4f44080` fixed backlog 16 to bounded 256, set the Gatling fork's module working directory, and made qualification/collection consume raw HTTP counts and assertion XML as well as harness outcomes. The 200-socket regression reproduced the original refusal and passed the fix. Original failing exits remain failed. A prior local `kubernetes-dev` dependency failure was not a passing baseline; current dependency-enabled local/CI results supersede that blocker.

## Cleanup, artifacts and remaining release gates

The wrapper and independent checks verified absence of **`msq-106d2c0f12c7`** and **`msq-106d2c0f12c7-data`**. After verifying no container references or repository tags, the exact session-pulled image reference was removed (exit 0; reference absent). No disposable session container, data volume or image reference remains. The wrapper's temporary SQL directory context completed normally. Public Maven dependency/build caches remain. No global prune, Docker reset, global resource change, unrelated resource removal, main change, merge or package publication occurred.

[Raw evidence ZIP](validation/qualification/2026-10-20/raw-evidence.zip) / [artifact metadata](validation/qualification/2026-10-20/artifact.json): **400 raw/metadata files from seven separate run records**, plus index/README; **1,928,952 bytes**; SHA-256 **`ab7ff892a1d8ceafab878deb977d1eacb7becf53bdca5f3dab71ef06eebbf92c`**. Canonical full `target/qm/QM.md`, evidence JSON, raw manifest, JUnit XML, PIT XML/HTML, Gatling HTML/binary logs/statistics, Oracle JSON, resource/cleanup proof and tested JAR checksums are preserved. The archive contains artifact hashes, **no Maven JAR distribution**. BOM-aware credential/token-shape and XML property-name checks flagged **0 files** before publication. Complete local archives remain in the sibling `qualification-evidence` directory; root `target/qm` holds this successful run's generated report. This repository archive supplies review access for a local run; it is not a full Actions artifact or CI attestation.

At 2026-10-20 17:45 UTC, GitHub listed only the default verification workflow; an authorized listing of main's root showed no `.github` directory. The environments API succeeded and returned **0 environments**. Full CI is therefore **NOT_RUN** with concrete setup prerequisites: owner-approved workflow-only registration on main and a protected nonproduction environment. An earlier path 404 was resolved through the authorized root listing, not treated as inability to run Oracle or as a credential workaround. Local Docker execution was explicitly approved and completed; external Oracle secrets were not its prerequisite. No environment protection was bypassed.

**Production remains NO-GO.** Remaining applicable [TODO gates](TODO.md) include: complete Oracle mixed JDBC/JPA rollback and crash/lock-timeout proof; leased/pending migration and backup/restore rehearsal; sustained multi-process/pod capacity, database-work economics, another-owner isolation and physical HTTP retry/MDM/bulk budgeting; observability/replay/security controls; independent consumers and supply-chain/release evidence. Inventory, broker and vendor delivery gates remain scope-dependent and unqualified when promised. The four-worker sample and in-memory semantic checks do not close those gates. Maintainer review is pending on PR #1. Stable publication would need passing evidence and tested artifacts for its exact requested source/configuration; this task publishes reports only.
