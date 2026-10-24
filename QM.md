# Production Oracle point-lock integration - 2026-10-24

**Default PASS; focused Oracle21/21 PASS; full NOT_RUN; production NO-GO.**
The opt-in policy is implemented in real adapters. This is scoped integration,
not full release qualification or complete bounded persistence. The original
plain-UPDATE cancellation failure, active contract/gate and reproducer are unchanged.
Accepted eb18209 diagnostic/report baba54c and all earlier evidence are preserved;
the complete baba54c QM follows as an unchanged historical suffix.

## Exact tested source and commands

Clean tested source **`2eea1d849dfd8d5f7f0ee1ee6f3386412b282477`**, tree
**`8338eff0badecc4be2b8855f1e4e9fedbc8da0d6`**, change digest
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
Repository Tobia5/model-sync-starter, branch review/layered-refactor-qm-20261020,
initial clean HEAD baba54c. No concurrent build, reset or unrelated edit.
Subsequent report-only changes are not claimed as tested source.

| Execution | Actual outcome |
|---|---|
| `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify` | Run **`fb25cfb1-075e-407a-8ca2-8bfb276278b3`**, exit0/107.286s, collector0. **141 JUnit tests/31 XML suites,72 Python guards**, zero failures/errors/skips; compilation, bounded local/H2 contracts, architecture, docs and profile/lifecycle/log/evidence checks passed. No expensive goals. |
| `python scripts/full-oracle-container.py --lock-policy-diagnostic` | Run **`d7f2d3fe-348c-41a5-89b3-0f7ed0785200`**, infrastructure attempt `021c1335-b225-4df6-963d-fea55337c9b8`; preflight/launcher/wrapper exit0,157.537s supervision. **21 JUnit launcher tests**:7 production integration/11.473s,7 retained DEFAULT atomicity/8.582s,7 inherited ORACLE_NOWAIT atomicity/9.429s. Zero failed/skipped/container failures. These are launcher summaries, not21 Failsafe XML tests. Strict detailed-suite evidence check also passed before source changed. |
| Git Bash `scripts/test-triggers.sh` | Exit0 at final source:86 core/43 trigger assertions; separate from JUnit counts. |
| Full/PIT/Gatling/million-row/A-B-C/prototype | **NOT_RUN** in this session. No earlier score/load result is attributed to this code. |

Diagnostic acknowledgments were invocation-only EXPLICIT_DISPOSABLE and
DISPOSABLE_NONPRODUCTION, with external Oracle variables cleared. The wrapper's
Maven subprocess used the same approved project settings, only `-Pfull -N initialize`
for guarded preflight, then selected the three classes. It did not run full verify.
Settings SHA256 `ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`;
user/global Maven settings and platform versions were unchanged.

## Implementation and responsibility review

- JDBC DEFAULT keeps ordinary point FOR UPDATE. Explicit ORACLE_NOWAIT validates
  an Oracle datasource and changes only the MS_WORK point lock. Queue SKIP LOCKED
  SQL is byte-identical to the baseline method. INSERT/UPDATE SQL is unchanged.
- JPA has explicit per-operation policy properties, a replaceable policy bean and
  a LockingJpaProjectionMapping capability. Opt-in rejects legacy mappings at
  construction. The sample explicitly consumes the bean; old two-argument store
  constructors remain DEFAULT. No global EntityManager timeout or production SQL
  inspector/native DML rewriter is shipped.
- Adapters recognize Oracle54 and produce framework-neutral LocalContention;
  the core contains no Oracle/Hibernate/Spring classification. Oracle code/state
  is copied into a new resource-free diagnostic SQLException: no entity-bearing
  Hibernate cause, session/connection or next-exception chain crosses lanes.
  Default exception behavior is preserved. Unit regressions cover that boundary.
- Failure persistence rejects an active attempt transaction. After rollback,
  LOCAL_CONTENTION uses the existing exponential retry-base/retry-max, 0.5-1 jitter,
  positive delay floor and failed-work max-attempts. It is a local work failure,
  not an owner/network failure. Metadata refusal propagates once with the original
  detached failure suppressed; no recursive write retry or permit refund.
- Ledger-before-projection ordering, tenant scoping, generation/lease fencing,
  local revisions and atomic commits remain. Owner reads remain outside DB
  transactions. Types stay top-level and module/layer/bean boundaries remain
  replaceable. This responsibility review is not a claim to prove all of SOLID.

The attempt limit applies when retry metadata can commit. A permanently locked
retry row cannot advance its counter: the lease remains recoverable and recovery
is lease-paced, potentially repeating owner reads/admissions. This is not a finite
global-attempt guarantee under perpetual metadata-write failure. Passive requests
cannot reset local contention backoff or revive DEAD; new explicit generations
retain existing semantics. Failed enqueue is not accepted, and a returned ID inside
an outer business transaction is provisional until its commit.

## Seven production integration outcomes

Times are attempt-start to refusal / outer transaction return (include guard/setup,
observation and rollback overhead), not isolated execute latency or production SLOs.

| Case | Observed outcome |
|---|---|
| Disabled compatibility | **PASS**, ordinary versioned lookup lock, plain refresh SELECT and ordinary UPDATE, successful IDLE completion; no NOWAIT SQL, healthy pool. |
| Failed existing-work enqueue | **PASS**, actual `select * from MS_WORK where work_id=:1 for update nowait` refused54, **12/85ms**. Exact SID61/serial281, previousSQL4rrcx6pxm100r. Guard write/lock rolled back, target generation1/WAITING retained, accepted=false,0 owner reads. A later explicit request after blocker cleanup reached generation2. |
| Result contention / cooldown / recovery | **PASS**, real mapping's version-qualified lookup `... for update of ce1_0.storage_key nowait` refused54, **7/13ms**, SID61/serial54156, previousSQLapad3w6hckwc8. Fresh projection remained before/version0 with no success state. Separate WAITING/attempts1/LOCAL_CONTENTION persisted; provider cooldown unchanged.1 prior owner read was retained; later recovery spent another admission/read and committed1 synchronization. |
| Retry metadata also locked | **PASS**, result point lock refused54, **5/10ms**, SID60/serial20807. The single separate metadata call refused54 in3ms and retained the original failure. Durable LEASED token/generation1/attempts0 stayed recoverable, projection before/no success; admission unchanged. After cleanup and explicit fixture expiry, another owner read completed1 sync. This is not natural lease-expiry timing proof. |
| Ordinary first inserts | **PASS for uncontended functionality**, first work/projection/state created and reached IDLE; actual JPA INSERT has no NOWAIT. Concurrent insert containment remains **UNQUALIFIED**. |
| Enabled success / refresh / safety | **PASS**, lookup NOWAIT followed by plain refresh SELECT. An independent observer immediately before flush refused54, proving the lookup's exclusive row lock persisted through refresh. New generation stayed WAITING, obsolete token rejected, local-change preserved with LOCAL_CONFLICT. Per-operation refresh options are separately asserted in default tests. |
| Actual Reactor worker | **PASS**,1 lane/persistence/owner thread, queue8, base200ms/max400ms/max-attempts3, default pacing250/500/1000ms and shutdown2s. With blocker held: **3 successful synthetic owner reads,0 completed syncs**, DEAD/attempts3/LOCAL_CONTENTION, generation1, projection before/no success.4 ticks/1 empty/3 failed/0 fetched/0 cancellation; maximum in-flight1. Stop reported DRAINED/0ms resolution, logical/physical/owner0; runtime sessions/pool independently idle/transaction-free. This is healthy local refusal shutdown, not a universal stalled-I/O drain qualification. |

For all three manual refusal cases, **before rollback with blocker held** the exact
session was INACTIVE/SQL*Net message from client/SQL_ID null/LOCK_WAIT0/TX_ACTIVE1.
After rollback it was LOCK_WAIT0/TX_ACTIVE0. Whole-transaction rollback and an earlier
guard write/lock release were independently inspected; all3 owned runtime session
identities remained observable, pool-active0, healthy subsequent query. No emergency
release occurred. Discrete exact-session snapshots demonstrate request return here,
not continuous tracing or universal server-termination bounds. Blocker release was
cleanup only and earns no mitigation credit.

### Refresh explanation and preserved failure

The first real run incorrectly required two locking SELECTs. The pinned provider
uses READ during refresh when it already holds the requested exclusive lock;
[Hibernate's refresh implementation](https://raw.githubusercontent.com/hibernate/hibernate-orm/7.4.5/hibernate-core/src/main/java/org/hibernate/event/internal/DefaultRefreshEventListener.java)
and the resolved sources-JAR hashes are retained. The corrected assertion requires
actual lock retention through refresh, rather than a redundant SQL clause. It does
not infer a bound from a hint or from a Java exception. No session-wide setting is
used. The old isolated unlocked-lookup prototype remains separate historical proof.
The recovery wait also incorrectly re-evaluated a state-changing runOne predicate;
its corrected observation is sticky and does not execute an extra attempt.

## Both-policy atomicity and precise coverage limits

All seven original OracleAtomicityIT assertions/bodies are unchanged. They passed
again with DEFAULT and, through NowaitOracleAtomicityIT inheritance, ORACLE_NOWAIT:
post-flush exception, ledger-completion rejection, transaction-completion rejection,
physical Oracle deferred-constraint COMMIT failure, successful paired commit,
local-version conflict and obsolete lease. Fresh independent transactions inspect
committed outcomes and separately persisted retry state. This retains the accepted
milestone and proves the newly selected policy under the same faults.

[Coverage matrix](docs/quality/LOCK-POLICY-COVERAGE.md) distinguishes existing work
point locks; first work insert; existing/missing projection; first reconciliation
state; quota registration/update; and extra flush targets. **Still unqualified:**
concurrent ordinary work/projection/state insert conflicts, quota row waits and
registration races, arbitrary mappings/additional tables/cascades/triggers/FK or
deferred checks, extra flush SQL, table locks, transport/COMMIT uncertainty and
universal server cleanup. Known external IDs do not eliminate local insert races.
Native Oracle26 DML NOWAIT remains an unshipped separate capability. No complete
bounded-persistence claim or million-row completion/capacity claim is made.

## Preserved attempts and source attribution

Every attempt retains its original log/exit, identity/tree/change digest and raw
reports. Later success does not change these outcomes:

- Development command setup failed exit1 before tests (PowerShell split an
  unquoted Maven property); corrected focused red run at baba54c plus recorded
  dirty-change digest executed4 new tests:3 failures/0 errors/1 pass, exit1.
- f32213f/tree8c502004, default0d3f9aea-04bb-4c33-b899-e47dbc36a39b,
  exit1/89.238s:24 JUnit/4 suites,1 error/0 failures,72 Python passed.
  New test wrongly assumed MemoryQuotaGate supplied an optional scheduling hint;
  corrected to check authoritative denied admission and retained grant/cooldown.
- 6465adc/treecde726b7, defaultf8ed800d-198d-4ae5-af56-d78f98c1748e,
  exit1/102.040s:139 JUnit/30 suites,1 error/0 failures,72 Python passed.
  New mock loop invoked its previous throwing stub while restubbing; fixed without
  relaxing the error classification assertion.
- 5fedde9/tree147edb6b, default14383024-8a93-4a2f-98f5-dba4b11a3cf2,
  exit0/114.869s,141 JUnit/72 Python. Diagnostic e476fe40-f285-4e14-bf07-8c50372e544c
  **FAILED**, exit1/147.559s,5/7; duplicate-refresh SQL assumption and one-shot wait
  caller failed. Atomicity classes did not execute in that attempt (NOT_RUN).
- 118526e/treee8eb79f0, default97804af5-ea78-4851-a79f-23a8c6ca04fc,
  exit0/112.618s,141 JUnit/72 Python. Diagnostic b7564920-214b-40cc-84c8-c626c379244c,
  exit0/153.230s:7 integration plus7 DEFAULT atomicity passed. It predates the
  detached-cause safety fix and the explicit enabled-policy atomicity variant;
  those results are not borrowed for final source.

## Environment, admission, cleanup and release decision

JDK21.0.12.1+1-LTS, Maven3.9.9, Python3.12.10, Boot4.1.1,
Hibernate7.4.5.Final, JUnit Jupiter/platform6.0.3, Surefire3.5.3,
Oracle26ai Free23.26.3.0.0/JDBC23.26.3.0.0, Docker29.8.2.
Pinned image `ghcr.io/gvenzl/oracle-free:23.26.3-slim@sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`.
Oracle6 GiB/4 CPU, JVM512 MiB heap/128 MiB direct; generated credentials,
synthetic targets, separate runtime/migration schemas, new labelled volume,
loopback-only port. No existing DB/volume/socket/host-directory mount.

Final post-build/collection admission eb11763a-19a5-4500-a0a5-eac5258e7845 observed
12.999/13.104/13.073 GiB available against unchanged12 GiB (6+2+4). Docker unused
container capacity19.989 GiB, C65.512/D1069.979 GiB free. Earlier bounded admissions
also passed; receipts retained. **Full18 GiB planning unchanged.** No unrelated
workload, container/network/volume, global settings, DNS/TLS or credential change.

Integration runtime pool3/observer2, acquire1s/connect1500ms/read10s. Manual attempt
TransactionTemplate timeout2s; actual manager default timeout was **-1**, explicitly
recorded. The option does not create a universal transaction deadline. Dataset is
one target/at most one guard per integration case plus the small inherited atomicity
fixtures; no large seed. No HTTP/provider load or throughput/percentile claim.

All three session pairs independently absent after owned cleanup:
msq-d8763410c914, msq-6b736e92dadb, msq-a125147b196d and each -data volume.
Exact no-such-object/no-such-volume receipts retained. Final owned pools, scheduler
threads and executor ended, physical remaining0, test table removed. Atomicity
fixtures dropped their owned tables/constraint. No observed owned residue.

**Full requalification NOT_RUN:** the path-scoped
[proposal](docs/quality/JDBC-CONTAINMENT-PROPOSAL.md) identifies the exact active
requirements that would change. Explicit reviewed contract acceptance is still
required before changing qualification semantics. The original active row-lock/
plain-UPDATE cancellation gate and reproducer remain unchanged, not relabelled PASS
or silently removed. New full gates add these cases/raw evidence to all existing
requirements; PIT keeps all8 targets/85% and adds the contention test class.
Applicable R01-R13 parents and reachable-path containment gaps remain open.
Production remains **NO-GO**. No full run, merge, main change, workflow bootstrap/
dispatch, package publication or broad refactor occurred.

[New immutable raw artifact/index](validation/qualification/2026-10-24-lock-policy/README.md)
contains source-bound attempts, SQL, logs, default XML/launcher summaries, manifests,
source-analysis hashes, admission/cleanup receipts and this run-scoped QM.

---

# NOWAIT diagnostic executed - 2026-10-24

**Default PASS; seven-case Oracle diagnostic PASS; full NOT_RUN; production NO-GO.**
Production adapters, configuration defaults and the original cancellation gate are
unchanged. The accepted `2c39b75` findings remain: independent cancellation FAILED,
network client containment measured, independent server termination unqualified,
and recovery required fixture cleanup. That matrix and full were not rerun.
The complete `a7a2bcf` report follows unchanged as history; all earlier evidence ZIPs
were checked byte-for-byte against that commit.

## Exact source and execution

Final clean tested source **`eb182094dc168573accb8c12f8e63264f771f192`**, tree
**`904b24d22194b2e4c303ce14538e8368f711b3ec`**, empty-change digest
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
The later report-only commit is not claimed as the tested source. Repository
`Tobia5/model-sync-starter`, branch `review/layered-refactor-qm-20261020`.
No concurrent build or unrelated source change was present.

| Execution | Source/run, actual exit and result |
|---|---|
| Initial matching default | Clean `a7a2bcf612057b363d33e985a81d50377751061e`, tree `e7a13f272f39d0d701a32eaaefe7f27f8eccac21`; run `13e65e23-efb2-4bf0-b727-64eb92d4dc6f`, exit0/83.465s, collector0.125 JUnit/27 suites,67 Python; zero failures/errors/skips. |
| First NOWAIT diagnostic | Same a7 source/tree, run `e96cd2cc-9dc7-40d9-9c25-4ea2e46c7625`, wrapper/launcher exit1/127.005s. Seven started,3 passed/4 failed,0 skipped/container failures. Four failed at claim before their intended operation: the SKIP LOCKED control had not disposed its claim, retaining the local provider reservation. JDBC point/refresh had three refusal proofs each; queue control also passed. This failed attempt is retained unchanged. |
| Final matching default | Final eb18209 source/tree above; run `ff2ad62c-9be7-40b2-bb7a-927db14c5036`, exit0/88.319s, collector0. **125 JUnit/27 suites,67 Python**, zero failures/errors/skips. Architecture, docs, profile/lifecycle/default-log and fresh-report guards passed; no expensive goals. |
| Final NOWAIT diagnostic | Final eb18209 source/tree; run **`c82fa300-a9e4-4a6e-ab9d-274d4f824936`**, infrastructure attempt `400fa8cf-c384-4bd3-bdba-4500941af774`; Maven preflight0, launcher0, wrapper0/120.358s. **7 found/started/passed,0 failures/skips/container failures**, launcher5581ms. |
| Standalone cheap contracts | Git Bash `scripts/test-triggers.sh`, exit0 at final source:86 core/43 trigger assertions, separately counted, not JUnit. |
| Full / PIT / Gatling / large seeds / old A/B/C | **NOT_RUN** in this session. No qualification credit is borrowed from older runs. |

Actual default command, serially before each diagnostic:
`mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify`.
Actual diagnostic command: **`python scripts/full-oracle-container.py --nowait-diagnostic`**.
Its Maven subprocess used the same project settings and only `-Pfull -N initialize`
for the existing guarded preflight, then the explicitly selected JUnit launcher.
It did not invoke full `clean verify`, PIT, Gatling or any large workload.
Invocation-only acknowledgments were `MODELSYNC_ORACLE_CONTAINER=EXPLICIT_DISPOSABLE`
and `MODELSYNC_FULL_ENV_ACK=DISPOSABLE_NONPRODUCTION`; external Oracle variables were
cleared. Settings SHA-256
`ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`;
user/global settings were not changed. Original exits/logs and source/run/file
manifests are retained separately for all four executions.

## Resource admission and pinned environment

Admission was checked after each matching default and collector finished, once
per source, with three bounded samples each. At a7:12.565/12.555/12.579 GiB free,
at eb:13.091/13.102/13.037 GiB. Both passed the unchanged **12 GiB** planning
requirement (Oracle6 + diagnostic2 + reserve4); **full18 GiB remains unchanged**.
Admission receipts `917f3a19-643e-4e43-9ff7-1a21e3e25383` and
`76e3e84e-9a79-46f6-938e-e74cb969ec2b` are preserved. At eb, Docker had19.980 GiB
capacity unused by containers; C65.753/D1069.987 GiB disk free. No unrelated
workload was stopped or global resource setting changed.

Oracle6 GiB/4 CPU; loopback-only published port; generated per-run credentials;
separate `MS_TEST_RUNTIME`/`MS_TEST_MIGRATION` schemas; new labelled session volume;
no host database/socket/directory mount. Diagnostic JVM heap512 MiB/direct128 MiB,
runtime pool3/observer2, one owned execution lane, query2s/read10s/observation5s,
lease60s/retry-base100ms/provider spacing1ms, worker/AOP disabled, Hibernate batching0.
Actual JDK21.0.12.1+1-LTS, Maven3.9.9, Python3.12.10, Boot4.1.1,
Hibernate7.4.5.Final, JUnit Jupiter/platform6.0.3, Surefire3.5.3, Docker29.8.2,
Oracle26ai Free23.26.3.0.0 / JDBC23.26.3.0.0. Image:
`ghcr.io/gvenzl/oracle-free:23.26.3-slim@sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`.

## Seven individual outcomes

The five refusal cases each made three attempts while the **same original blocker
remained held**. Times below are **attempt start to refusal / outer transaction
return**, in ms; they include connection/guard work and observer/rollback overhead,
not isolated JDBC execute latency. They are observations, not new production SLOs.

| Case | Actual SQL/control and individual result | Refusal ms / transaction-end ms |
|---|---|---|
| JDBC point lock | **PASS**, exact `select * from MS_WORK where work_id=? for update nowait`; Oracle54 translated to Spring `PessimisticLockingFailureException`. No projection mutation/DML. Retry-metadata point lock also refused54 while the ledger blocker stayed held, leaving the durable lease recoverable. |17,5,4 /66,8,7 |
| JPA mapping lookup | **PASS**, generated version-qualified `select ce1_0.storage_key ... where ... storage_key=? and ... local_version=? for update of ce1_0.storage_key nowait`; Oracle54 through Hibernate/JPA LockTimeoutException. No mapping update, refresh continuation or projection DML after refusal. |6,7,6 /9,10,9 |
| Actual JPA refresh | **PASS**, deliberately unlocked lookup isolates the real store's `em.refresh`; session timeout0 generated full-column `select ... where ... storage_key=? for update of ce1_0.storage_key nowait`; Oracle54/JPA LockTimeoutException, no update/DML continuation. This is proof for the fixture's session configuration, not inference from a hint. |10,8,7 /14,11,10 |
| Flush with an extra dirty row | **PASS within fixture**, two versioned `update sample_customer set ... where storage_key=? and local_version=? nowait` statements submitted; the separately blocked extra row refused54. No SQL followed refusal before rollback. Native DML extension explicitly enabled only for this single synthetic table; earlier attempt-owned ledger write/lock rolled back. |12,10,10 /16,13,14 |
| Racing projection insert | **PASS within fixture**, actual `insert into sample_customer (...) values (?,?,?,?,?,?,?,?) nowait` refused54 against the still-uncommitted duplicate insert. SELECT NOWAIT alone did not provide this containment; native DML extension did. |13,8,7 /16,10,10 |
| Queue SKIP LOCKED control | **PASS**, an unlocked later item was claimed while the earlier item stayed locked. Real WAITING/binding-ready/recovery cursor SQL retains `for update skip locked`. Disposing the control claim through `failed(RetryLater)` consumed no owner permit; a follow-up urgent claim succeeded. This control has no refusal/transaction timing or exact-session trace measurement. |Not separately timed; not a refusal bound |
| Success/generation/fencing/local revision | **PASS**, normal locked lookup/refresh then ordinary versioned UPDATE; newer request remains WAITING, old token rejected, a concurrent local revision remains `local-change` with LOCAL_CONFLICT. This control has no contention timing/session-bound measurement. |Not separately timed; no refusal expected |

Full submitted SQL, error chains, individual values and original snapshots are in
[raw evidence and observed SQL](validation/qualification/2026-10-24-nowait-execution/README.md).
These are library-coordinator persistence probes with a synthetic owner outside
transactions; refusal attempts are not provider-admitted calls or sync throughput.

### Server request, rollback and pool evidence

All15 refusal attempts reused exact runtime **SID220/serial8623**. Before rollback,
while each blocker remained held: `STATUS=INACTIVE`, `EVENT=SQL*Net message from client`,
`STATE=WAITING`, `SQL_ID=null`, `LOCK_WAIT=0`, `TRANSACTION_ACTIVE=1`.
The SQL*Net idle wait is not a TX row-lock wait. `PREV_SQL_ID` was respectively
JDBC`4rrcx6pxm100r`, lookup`apad3w6hckwc8`, refresh`d7btmgvwcr0ak`,
extra-row UPDATE`a5nx5hysw1v17`, INSERT`c57ndxp9jwvtx`.
After rollback, the same exact session still had no row-lock wait and
`TRANSACTION_ACTIVE=0`; no outstanding waits accumulated across the three attempts.
These are discrete exact-session snapshots, not a continuously traced server-duration
measurement. They show request return without blocker release in this fixture.

In15/15: transaction synchronization reported ROLLED_BACK, an earlier ledger guard
write was absent through a fresh connection and its lock could be reacquired NOWAIT,
original target LEASED status/token/generation persisted, and no successful MS_STATE
existed. No SQL was submitted after refusal before rollback. Physical activity and
runtime pool active counts returned to0. The physical connection stayed open and
subsequent pool queries succeeded; reuse of the same SID/serial across all attempts
is observed. All five sequences then recovered and rejected old tokens **after
cleanup**, using an explicit fixture expiry/due transition. This is not natural
lease-expiry timing or pre-cleanup recovery proof. JPA failures separately persisted
WAITING/attempts1/FETCH_FAILED under the existing policy; contended ledger metadata
could not persist until recovery. Provider cooldown/backoff semantics were not changed.

The raw queue cursor list also contains its own metadata query (`fqkda3cr4fg4q`).
It is identified separately and excluded from the readable production SQL extract.
Raw cumulative cursor executions are retained **without** conversion to interval
deltas, selector callbacks, native DB work or performance claims.

## Measured candidate decision and remaining gaps

**Supported by this evidence:** opt-in database NOWAIT refusal for the existing-row
JDBC point lock and this mapping/session-configured JPA lookup/refresh; separately,
Oracle26 native UPDATE/INSERT NOWAIT on the sample's single-table, nonbatched mapping,
including one extra dirty row and one immediate unique insert conflict. The SQL
actually reached Oracle; all15 refusals were54, not transport/parser errors or a
Java-only guard exception. Queue semantics and success safety controls passed.

**Smallest production change justified for review, not implemented:** an explicit
Oracle point-lock NOWAIT option in the JDBC adapter and explicit lookup/refresh
lock-property support in the mapping/JPA adapter. Keep ledger-before-projection
ordering and classify refusal as contention with bounded, durable retry behavior;
retry-state persistence can itself refuse. A generic consumer SQL inspector is not
qualified. Any native DML support needs version/capability checks and exact generated
SQL coverage; it is a separate narrow extension, not necessary to ship the initial
existing-row option. No adapter/API/configuration change is made by this diagnostic.

**Still unqualified:** new ledger/state/quota insert conflicts, older Oracle versions,
arbitrary mappings/additional tables, batching/RETURNING, custom adapter SQL, FK and
deferred constraints, cascades/triggers, table locks, network loss/uncertain COMMIT,
and universal server cleanup. Existing-row NOWAIT does not repair the original plain
locked UPDATE cancellation test. The original active gate stays FAILED/open; no
replacement contract is approved. Accepted atomicity, selection and worker milestones
are preserved; applicable R01-R13 parents remain open. Production remains **NO-GO**.

## Owned cleanup and immutable evidence

First attempt: `msq-005024c39cfa` / `msq-005024c39cfa-data`; final:
`msq-4999761759ec` / `msq-4999761759ec-data`. Both wrappers verified removal; independent
post-run inspections returned the exact no-such-object/no-such-volume diagnostics.
Final executor terminated, physical remaining0, owned pools closed and owned table
removed. **No emergency lock release** occurred; blocker rollback happened only after
refusal/rollback/pool proof and earns no mitigation credit. No residual owned resource
was observed. Unrelated containers, volumes, networks and processes were untouched.

[New raw execution archive](validation/qualification/2026-10-24-nowait-execution/README.md)
retains both matching defaults, the original failed diagnostic, final passing
seven-case diagnostic, admission/cleanup receipts, original and UTF-8-normalized logs,
source/run/file manifests and this run-scoped summary. Historical blocked evidence,
`ab7ae1d`, `a7a2bcf`, `2c39b75`, `43040e7` and older qualifications are unchanged.
No full run, million-row seed, workflow dispatch, main change, merge or publication.

---

# Database NOWAIT prototype — 2026-10-24

**Default PASS · New Oracle diagnostic BLOCKED / NOT_RUN · A/B/C and full NOT_RUN · Production NO-GO.**
The JDBC/JPA locking audit and executable, isolated prototype are implemented.
No Oracle container was provisioned: available host memory was below the retained
12 GiB small-run planning budget. There is no measured NOWAIT decision or generated
Hibernate SQL from Oracle in this attempt. The original failing gate and accepted
`2c39b75` findings remain unchanged. The complete `43040e7` report follows verbatim
as history; its evidence ZIPs and production persistence code were hash-checked.

## Actual source, commands, results and blocker

Tested clean source **`ab7ae1d0bfb15670b6de3dc8205573c9fc04844c`**, tree
**`4601bfdfab27e849bd15ce96647b4689896009f0`**, change digest
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
The later documentation/evidence commit is not claimed as tested source.

| Execution | Evidence / actual outcome |
|---|---|
| Initial prototype default | Source `731d558a98985881c601b7a137747227e7f836fa`, tree `186985bbb3178ae5e246a8b9dc9f81a43cfccfcc`, run `c37466c0-899e-473b-ae45-662484a9f6e7`; same default command below, exit1/74.577s. New queue assertion failed Java generic type inference. The source fix is retained; the failing log/partial reports are not relabelled passing. No Oracle ran. |
| Corrected default | `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify`; run `dc582cf4-df8a-4b56-b198-4a7d03f094e8`, exit0/102.511s, collector0. **125 JUnit tests/27 suites,67 Python guards**, zero failures/errors/skips. Architecture, docs, lifecycle separation and fresh-report guards passed. No expensive goals executed. |
| Standalone development checks | Git Bash `scripts/test-triggers.sh`, exit0:86 core/43 trigger assertions (not JUnit counts). Architecture/docs and three focused Python scope guards also passed. |
| Scoped resource preflight | Attempt `b775f679-54e7-4c24-81da-16d13643cff4`,2026-10-24T09:07:40–47Z;11.410/11.402/11.430 GiB available versus12 GiB required. **BLOCKED**, Python2/enclosing PowerShell1. Docker29.8.2,8 CPUs,25201692672 configured bytes,20.088 GiB capacity unused by containers; C69.397/D1069.990 GiB disk free. No unrelated resource was changed. |
| Planned `python scripts/full-oracle-container.py --nowait-diagnostic` | **NOT_RUN**; resource preflight stopped before wrapper/provisioning. No Oracle run identity, container, volume, schema or database was created by this attempt. |

Project-scoped Central settings remain explicit, SHA-256
`ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`;
user/global settings were not modified. No platform/dependency version was changed.
The diagnostic retains512 MiB heap/128 MiB direct, Oracle6 GiB/4 CPU, and the
scoped6+2+4 GiB budget. Full18 GiB planning remains unchanged. The blocker is
host headroom; it is not missing external credentials or a denied Oracle service.

## Audit and executable scope

[Audited locking paths](docs/quality/NOWAIT-PERSISTENCE-AUDIT.md):

- JDBC `locked()` uses plain `FOR UPDATE` for point validation/enqueue/completion/
  reschedule. The test-only JdbcTemplate seam changes only that exact query to
  `FOR UPDATE NOWAIT` while exercising the real `JdbcWorkStore`.
- Queue WAITING/expired-lease `SKIP LOCKED` paths are unchanged and have a separate
  small control. No queue design or prior selection milestone is reopened.
- JPA delegates lookup locking to the mapping, refreshes with PESSIMISTIC_WRITE
  without explicit per-call wait properties, then flushes. The fixture provides
  explicit mapping/session timeout0 and records generated SQL; SQL without NOWAIT
  cannot be treated as bounded merely because a hint was present.
- A deliberately deferred lookup isolates the real store's refresh; extra-row
  mutation exposes flush targets not protected by a primary-row lock. Native
  DML NOWAIT is prototyped only for the synthetic table, including an uncommitted
  projection insert conflict. This syntax is documented for Oracle26, but support
  on the pinned image is **unmeasured**, not presumed.

Seven explicitly selected tests compile; each refusal sequence permits three
attempts with the same blocker held. They require real Oracle54, client/transaction
timing, exact SID/serial state before and after rollback, no later SQL after refusal,
release of earlier ledger guard writes/locks, healthy pool behavior, durable intent
and post-cleanup recovery. Generation, old-token and local-revision controls remain.
No owner call occurs under a local transaction. Uncertain physical completion
stops subsequent probes; cleanup cannot earn mitigation credit.

All seven Oracle tests are **NOT_RUN**. In particular, database refusal, generated
SQL, absence of server waits, rollback and recovery under this policy are not yet
verified. Current default regressions are not substituted for those measurements.

## Limits and decision

**Candidate decision: BLOCKED / UNMEASURED.** This is an isolated test prototype,
not a shipped SQL-rewriting policy. Future adapter/API changes require explicit
Oracle-aware point-lock configuration and a mapping/refresh contract, plus reviewed
coverage of flush SQL. Native DML generation cannot be silently generalized.
Existing-row SELECT NOWAIT cannot qualify missing-key/unique/FK races, new ledger/
state inserts, additional mapped tables, cascades/triggers/deferred constraints,
custom SQL, table locks, network failure or uncertain COMMIT. No DDL_LOCK_TIMEOUT,
privileged session killing, application-owned datasource/global changes or
unbounded replacement attempts were introduced.

[The containment proposal](docs/quality/JDBC-CONTAINMENT-PROPOSAL.md) records this
pending route and leaves the original active gate unchanged. Restore roughly1 GiB
of headroom, then obtain a fresh default at the current review head and run only
the new diagnostic. No further generic local-run approval is required. Do not run
the accepted A/B/C matrix or full as part of this task. Production remains NO-GO;
no merge, main change, workflow dispatch or package publication occurred.

[Raw default/failure/resource evidence](validation/qualification/2026-10-24-nowait/README.md):
133 files/246673 bytes; SHA-256
`792d6594b80eff76390ce5dda393db2659918245191b79943c352d7c91569eab`.
Original exits/logs, source/run-bound reports, hash manifests, capacity and baseline
preservation receipts retained. ZIP CRC/per-file equality and input hashes verified;
bounded generated48-hex/XML-secret-property scans found0 (not a universal audit).

---

# Corrected focused JDBC diagnostic completed — 2026-10-23

**Default PASS; six diagnostic evidence checks PASS. Independent cancellation
FAILED; network client containment measured. Oracle server work remained waiting.
Full NOT_RUN; production NO-GO.** The corrected experiment completed after headroom
recovered. It verifies committed state, connection disposal, subsequent work and
owned-resource cleanup. It does not resolve the existing independent cancellation
gate or demonstrate a server-termination bound. No full run was repeated.

## Exact tested source and execution

Both current executions tested clean source
**`2c39b759cd9295c35eeddf6587094816950ccdf3`**, tree
**`03f1aa3e069863207151b28f2389f899f089e7fb`**, change digest
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
This later documentation/evidence commit is not claimed as tested code.

| Execution | Identity / command | Actual result |
|---|---|---|
| Default | Run `2ceaca58-96aa-4e1b-984d-5be4869b1d84`; `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify` | Exit0/113.698s, collector0;121 JUnit/26 suites,64 Python guards; zero failures/errors/skips. No expensive execution. |
| Focused diagnostic | Run `31f8e5ee-aa5f-4ec3-920d-4b952f90c089`, container attempt `56d20d55-638d-40ff-8357-0c720951467f`; `python scripts/full-oracle-container.py --cancellation-diagnostic` | Root Maven `-Pfull -N initialize` preflight0; launcher/wrapper/supervisor0;175.557s overall,48.145s suite;6 found/started/succeeded,0 failures/skips/container failures. **Evidence collection only**, not cancellation/full qualification. |

The initial failed classifier run and both intermediate resource blocks remain
below with unchanged raw artifacts. No report from those attempts supplies a
passing result to this run. The corrected run first obtained a fresh default at
the current head. Its scoped capacity receipt observed13.244–13.425 GiB free,
19.950 GiB Docker capacity unused by containers, C87.561/D1069.993 GiB free disk.
Declared planning remained12 GiB for the small launcher (6 Oracle +2 runtime +4
reserve),18 GiB for full. Oracle stayed6 GiB/4 CPU; JVM512 MiB heap/128 MiB direct.
No unrelated resource/global setting changes were made.

## Configurations and A/B/C outcomes

Pinned JDK21.0.12.1+1-LTS, Boot4.1.1, Spring JDBC7.0.9, Hikari7.0.2,
ojdbc11/Oracle Free23.26.3.0.0. Generated runtime/migration schemas, synthetic
one-row fixtures and loopback ports used the existing digest-pinned container
entry point. Maven Central settings/digest remain those recorded below. Observer
pool2, measured Hikari pool1, acquisition1000ms, validation500ms, maxLifetime0;
direct JDBC unpooled. Query timeout2s, socket read10s, connect1500ms,
`disableOob=true`, observation boundary5s. C alone set network timeout3s and
verified the driver's effective timeout as3000ms. No deadline/platform change.

| Probe | Direct JDBC | Existing Spring/Hikari path | Outcome |
|---|---|---|---|
| A automatic timeout | Client exit10007ms |10013ms | Both still executing at5s: independent cancellation **FAILED**. |
| B cancel once after confirmed lock wait | Cancel invoked at50ms, returned0ms; client exit10017ms | At23ms, returned0ms; client exit10008ms | Independent cancellation **FAILED**.0ms has millisecond resolution; return is not completion. |
| C same locked DML + network3s | Client exit3007ms |3016ms | Client I/O containment **PASS in this fixture**; not independent statement/server cancellation. |

All six returned recoverable18730/SQLState08006 with
`IOReadTimeoutException` / `SocketTimeoutException`, and their physical
connections were closed. Spring's attempted rollback on a closed connection
failed; this alone proves no transaction outcome. Fresh reads after cleanup
observed the unchanged committed row in6/6, and new healthy-connection updates
committed successfully in6/6. Broken connections were disposed, not reused.

### Server work and physical completion

**After client exit, all six exact Oracle SID/serial sessions were still
ACTIVE / WAITING on `enq: TX - row lock contention`.** Client execution exit
and connection disposal therefore do not prove server-side cancellation or
successful physical database drain. Direct reproduction establishes that
Spring/Hikari is not necessary for this failure. The exact driver/server/
forwarding cancellation-response cause remains unknown; no specific defect is
asserted. At5s the owned client thread was in Oracle NIO socket read and the
timeout monitor existed.

The competing lock stayed held until client exit. Cleanup then rolled it back;
the server lock wait was no longer observed4–9ms later. Those observations are
**release-assisted cleanup**, not independent server-exit/cancellation evidence.
No emergency unlock was required to make a client call exit, and no blocker
release was credited as cancellation. An independent server cleanup bound
remains unqualified. The existing failing gate is unchanged.

All owned executors terminated; measured Hikari pools returned to0 active and
closed; the observer pool closed; the fixture table was removed and absence
checked. Wrapper cleanup removed `msq-f6dafe3a2248` and
`msq-f6dafe3a2248-data`; independent exact-name inspections confirmed both absent
at2026-10-23T07:46:35.755316Z. The first attempt's pair was independently absent
as well. No residual session-created Docker resources are known.

## Contract review and remaining gates

[The explicit proposal](docs/quality/JDBC-CONTAINMENT-PROPOSAL.md) remains
**NOT APPROVED / NOT IMPLEMENTED**. It proposes query2s plus network3s with
mandatory disposal/fresh-state/recovery proof, while retaining separate server
work accounting and an open server cleanup/recovery bound. The measured
alternative contains client I/O; it does not replace physical server termination.
Oracle documents [cancellation's response dependency](https://docs.oracle.com/en/database/oracle/oracle-database/19/jjdbc/JDBC-troubleshooting.html)
and [error18730](https://docs.oracle.com/en/error-help/db/ora-18730/). No arbitrary
exception, timeout extension, skip, lower mutation gate or smaller required full
workload was accepted to obtain these results.

Do not run full until the issue is resolved under the existing contract or an
explicit replacement is approved and implemented. Default PASS and diagnostic
collection PASS are not full qualification. Production remains **NO-GO**; prior
accepted atomicity/selection/pacing/cursor milestones and other parent gates are
unchanged. No merge, main change, workflow dispatch or publication occurred.

## Raw evidence

[Corrected evidence and preserved earlier attempts](validation/qualification/2026-10-23-jdbc-cancellation/README.md):
`corrected-evidence.zip`,86 files/158481 bytes, SHA-256
`392741498cbbfbf1a38b56e37b88497ff319eccc6c77a935c8de8a88b6605f25`.
Includes current default/diagnostic original logs and manifests, source/run JSON,
capacity and independent cleanup receipts, and run-scoped Markdown copied to
`target/qm/QM.md`. Per-file hashes, ZIP CRC and archived byte equality verified;
bounded generated-credential/XML-property scans found0, not a universal secret
guarantee. Earlier038fa579/8533f34c archives and reviewed baseline ZIPs are
unchanged. Original failures and resource blocks follow as history.

---

# Focused locked-JDBC execution — 2026-10-23 (earlier attempt)

**Latest default PASS · Executed diagnostic FAILED · Corrected Oracle run BLOCKED / NOT_RUN · New full NOT_RUN · Production NO-GO.**
The small disposable experiment actually ran. It reproduces the independent
cancellation failure through direct JDBC as well as Spring/Hikari, and measures
separate network containment of the same locked DML. Fresh committed-state and
subsequent-work verification did not execute because the first diagnostic's
classifier rejected the pinned driver's specific18730 error. This failure is
retained. The corrected harness is default-green but its Oracle run is blocked
by memory. No supported production fix or complete containment qualification is
claimed. The original full gate, historical reports and archives remain unchanged.

## Exact sources, attempts, commands and exits

All three sources below have the clean change digest
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
The later report commit is not claimed as any tested source.

| Execution | Source/tree and identity | Actual outcome |
|---|---|---|
| Default before Oracle | `9b2476653a1f1364ea35d52f916e706910428ff3` / `704b10350a9b6a4b204c9b45daee33c05e7a51ac`; run `32f5ed38-bf92-4013-a803-2dfd6d1210fd` | `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify`; exit0/101.681s, collector0;117 JUnit/25 suites,64 Python; no failures/errors/skips. |
| Actual small Oracle diagnostic | Same `9b24766` source/tree; run `8bf36376-0d7b-45ef-9c12-c503aacdea5f`, container attempt `e28be974-f96b-4a89-86e7-1f91530dddb4` | `python scripts/full-oracle-container.py --cancellation-diagnostic`; Maven root `-Pfull -N initialize` preflight0, selected launcher1, wrapper1, supervisor1;203.290s. Six tests found/started/FAILED, zero succeeded/skipped/container failures;47.683s suite. Full NOT_RUN. |
| Corrected-harness default | `6edcf844ff1d9bdae5876d918e22c31b9619a346` / `0c17f0fcb1f40c310334281ed0edfc502e9322c0`; run `76243cef-7f67-4a57-8937-5062c1d70274` | Same default Maven command; exit0/92.497s, collector0;**121 JUnit/26 suites**,64 Python; zero failures/errors/skips. Includes four classifier regressions. |
| Corrected-harness Oracle preflight | Same `6edcf84` source/tree; capacity attempt `a3348b29-b137-4a90-9c41-5f09f777906b`,2026-10-23T07:32:42–48Z | BLOCKED, Python2/PowerShell1;11.474/11.493/11.477 GiB free versus declared12 GiB. Wrapper NOT_RUN; no new Oracle run ID/resources. Earlier Oracle output is not attributed to this source. |

The earlier `5b898c3` default and18 GiB resource block are preserved below and in
the original archive. The18 GiB figure budgets for full/PIT/Gatling. Before the
small run, the diagnostic JVM was explicitly capped at512 MiB heap/128 MiB direct
memory; scoped planning reserved6 GiB Oracle +2 GiB launcher/runtime +4 GiB host
reserve. The full18 GiB budget and Oracle6 GiB/4 CPU caps were unchanged. This
scoped preflight passed with12.316–12.371 GiB free,19.950 GiB Docker capacity
unused by containers, C93.583/D1069.988 GiB free disk. The later scoped failure
was not retried unchanged to seek admission. Unrelated resources/global settings
were never changed. PIT, Gatling, recovery suites and large seeds did not run.

## A/B/C observations: direct JDBC versus Spring/Hikari

Each probe used one locked synthetic row, explicit rollback-only transaction,
query timeout2s, socket read10s, connect1500ms, existing `disableOob=true`, and a
5s observation boundary. Oracle confirmed the exact test-owned blocking session
before manual cancel. C alone used supported `Connection.setNetworkTimeout`3s;
the driver's effective timeout was verified as3000ms versus10000ms for A/B.
The measured platform stayed pinned: JDK21.0.12.1+1-LTS, Boot4.1.1,
Spring JDBC7.0.9, Hikari7.0.2 and ojdbc11 23.26.3.0.0; Oracle reports
26ai Free23.26.3.0.0. Observer pool size2, measured Hikari pool size1,
acquisition1000ms, validation500ms and maxLifetime0 were fixture-owned. Direct
JDBC was unpooled. Each probe owned one execution, one cancel and one network
executor. The launcher reported max heap536870912 bytes; no platform upgrade
or application-owned datasource change was made.

| Diagnostic | Direct JDBC | Spring/Hikari | Meaning |
|---|---|---|---|
| A automatic query timeout | Client execute exit10007ms; still active at5s |10003ms; still active at5s | **Independent cancellation FAILED** in both paths. Effective query timeout2s. |
| B manual `Statement.cancel()` once | Invocation at65ms, returned0ms; client execute exit10015ms | Invocation at3ms, returned0ms; client execute exit10017ms | **Independent cancellation FAILED**. A returned cancel call is not physical execution completion;0ms is millisecond-resolution timing. |
| C same locked DML + network3s | Client execute exit3002ms |3009ms | **Client I/O bound observed**, not independent statement cancellation or complete containment PASS. |

All six executions ended with `SQLRecoverableException`, Oracle18730/SQLState08006,
through `IOReadTimeoutException` / `SocketTimeoutException`; all physical connections
were closed. Spring additionally reported failure to roll back the already closed
connection. This is not successful rollback proof. At5s, the owned execute thread
was in Oracle NIO socket read and the Oracle timeout monitor existed. Manual cancel
returned promptly. Direct reproduction rules out Spring/Hikari as a necessary cause;
the exact driver/server/forwarding cancellation-response failure remains **unknown**.

### Safety and cleanup evidence

No emergency blocker release was needed to make the client calls exit: the locks
were retained until natural read-timeout/client exit. Their subsequent release is
not credited as cancellation. Every owned executor ended; measured Hikari pools had
zero active connections and closed; the observer pool closed; the fixture table
was dropped and its absence checked. Container `msq-c9233f5409d1` and volume
`msq-c9233f5409d1-data` were removed by the wrapper and independently confirmed absent
at2026-10-23T07:31:03.954015Z. No residual session-created Docker resource is known.

**Not verified:** fresh committed row after the failed DML, successful subsequent
work, and the server request's state before blocker release. All six tests stopped
at the unexpected-error classifier before those assertions. The intermediate raw
C field `layered_containment=PASS` describes only the observed client bound;
the case is INCOMPLETE and JUnit FAILED. It is **not** qualification. Raw evidence
is unchanged. Corrected source narrowly recognizes documented recoverable18730/17002
only with a socket-timeout cause, tests rejection of arbitrary errors, moves fresh
state/recovery before classification, and records exact Oracle SID/serial state.
That Oracle execution is NOT_RUN; none of these new checks is claimed passing.

## Proposed contract and remaining prerequisite

[The explicit containment draft](docs/quality/JDBC-CONTAINMENT-PROPOSAL.md) is
**NOT APPROVED / NOT IMPLEMENTED**. It would replace independent ORA-01013 proof in
this locked-DML fixture with query2s plus network3s, physical client exit within5s,
mandatory connection disposal and fresh-state/recovery proof, while keeping server
work separately accounted for. Oracle documents [cancellation limits](https://docs.oracle.com/en/database/oracle/oracle-database/19/jjdbc/JDBC-troubleshooting.html)
and [error18730](https://docs.oracle.com/en/error-help/db/ora-18730/). The draft requires
the missing safety measurements before acceptance; a read-timeout exception alone
cannot approve it. All existing product/quality gates remain intact.

**Next action:** free at least0.53 GiB to restore the declared12 GiB diagnostic
headroom, then execute the corrected focused candidate once. No new generic local
run approval is needed. Do not run full until the original issue is resolved or
an explicit replacement contract is approved and implemented. Production remains
NO-GO; accepted atomicity, selector, pacing and cursor milestones are not reopened.

[Raw execution evidence](validation/qualification/2026-10-23-jdbc-cancellation/README.md):
`execution-evidence.zip`,154 files/289658 bytes, SHA-256
`8533f34c4b64e25c3f294976aeaabd262341ded358256aba369ccfb72dcfcb40`.
Original exits/logs and source/run-bound partial JSON are retained alongside both
defaults, scoped capacity receipts and independent cleanup. CRC/per-file equality
and input hashes verified; bounded generated-credential/XML-property scans found0.
The initial archive and earlier full/Q4 ZIP hashes were independently rechecked.
No merge, main change, workflow dispatch or package publication occurred.

---

# Focused JDBC diagnostic preparation — 2026-10-23 (earlier attempt)

**Default PASS · Focused Oracle diagnostic BLOCKED / NOT_RUN · New full NOT_RUN · Production NO-GO.**
No Oracle diagnostic was executed in this attempt: available host memory after
Maven exited was **12.185 GiB**, below the retained **18 GiB** session planning
budget (6 GiB Oracle + 8 GiB build/runtime + 4 GiB reserve). No session container,
volume, database or schema was created. No unrelated resources or global settings
were changed. The original failing statement gate and every earlier report/archive
remain unchanged. This is not new runtime evidence about the reviewed full failure.

## Exact tested source and execution

Tested clean source **`5b898c3694ef6f9a287e2d2bba0bcde955181deb`**, tree
**`8d28ebead9413f98af9dc6a4385ed90b0c78f919`**, change digest
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
The later report commit is not claimed as this tested source.

| Check | Actual result |
|---|---|
| `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify` | PASS, exit0; supervisor106.067s; run `177ed7a8-1c51-459f-913b-74a22f48ecf9`; 117 JUnit tests /25 XML suites, zero failures/errors/skips;64 Python guards. Collector exit0. No expensive goals executed. |
| Standalone Python guards / architecture / docs | All PASS, exit0;64 guards in51.479s;15-module graph and16 Markdown documents checked. |
| `bash scripts/test-triggers.sh` | Initial WSL bash exit1 (`javac` absent); explicit Git Bash with existing Windows JDK exit0,86 core /43 trigger assertions. These are not additional JUnit tests. |
| Resource preflight after default | BLOCKED, Python exit2 (PowerShell pipeline exit1); attempt `ed85ad37-0c24-4f7f-9ff4-da8951a58446`,2026-10-23T07:17:23.6328513Z,12.185 GiB available. Earlier concurrent-build samples11.259–12.798 GiB retained separately. |
| `python scripts/full-oracle-container.py --cancellation-diagnostic` | **NOT_RUN**; provisioning prevented by resource preflight. No diagnostic run ID or measured cancellation/containment result exists. |
| Full / PIT / Gatling / large Oracle workloads | **NOT_RUN** in this attempt. Earlier full failure remains in the unchanged report below. |

Maven uses only the reviewed project-scoped Central settings, SHA-256
`ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`;
user/global settings were not modified. The resolved default classpath records
JDK21.0.12.1+1-LTS, Boot4.1.1, Spring JDBC7.0.9, Hikari7.0.2,
ojdbc11 23.26.3.0.0 and JUnit Platform6.0.3. The compiled candidate retains
these versions. Docker
preflight observed server29.8.2,8 CPUs,25201692672 configured bytes; free disk
C93.607/D1069.990 GiB. Seven running containers were counted without inspecting or
changing them. This is not permission to remove another project's resources.

## Diagnostic candidate, configurations and unknowns

The explicit launcher compiles six one-row probes, with direct JDBC and the existing
Spring `JdbcTemplate` / `DataSourceTransactionManager` / Hikari path for each case.
It selects no other Oracle suite, PIT, Gatling or million-row fixture. JUnit success
would mean checked evidence collection; individual deadline/containment outcomes
are reported separately and cannot qualify the original full gate.

| Case | Configuration prepared | Observed outcome |
|---|---|---|
| A automatic timeout | Query2s, socket read10s, observation5s; existing in-band cancellation setting | NOT_RUN |
| B manual cancel | Same configuration; confirm exact test-owned Oracle row-lock wait, invoke `Statement.cancel()` once from separate owned thread | NOT_RUN |
| C layered containment | Same locked DML/query2s/read10s; separately set JDBC `Connection.setNetworkTimeout`3s, observation5s | NOT_RUN; never independent statement-cancellation PASS |

The candidate records cancel invocation duration, physical execute exit, transaction
cleanup, connection disposal/reuse, fresh committed state, subsequent committed work,
pool/thread termination and emergency unlock independently. Post-boundary cleanup
observation cannot extend the5s gate; releasing the blocker earns no cancellation
credit. No physical-completion, rollback or containment claim can yet be made.

Oracle documents that query timeout uses cancellation and that cancellation depends
on a database/network response ([driver troubleshooting](https://docs.oracle.com/en/database/oracle/oracle-database/19/jjdbc/JDBC-troubleshooting.html)).
`setNetworkTimeout` is a separate JDBC connection control
([Oracle API](https://docs.oracle.com/en/database/oracle/oracle-database/26/jajdb/oracle/jdbc/OracleConnection.html)).
These API contracts do not establish the cause of this particular failure. That
cause, direct-versus-Spring comparison and a supported fix or measured replacement
contract remain **unknown pending execution**. No replacement contract is proposed
as measured or implemented, and the original failing gate is unchanged.

**Next prerequisite:** restore at least18 GiB available host memory without changing
the approved caps or unrelated resources. Then execute the focused candidate;
do not repeat full until the issue is resolved under the existing contract or an
explicitly approved replacement. Accepted architecture/atomicity/SQL/pacing/cursor
milestones and all parent production release gates are unchanged.

[Source-bound raw evidence and resource receipts](validation/qualification/2026-10-23-jdbc-cancellation/README.md)
include the original default log, fresh reports, supervisor, manifests and both
capacity observations. ZIP SHA-256
`038fa5790cf7d98eb862336977f252542577ddbe67f061d1683a07779c3165a7`;
137653 bytes /73 files; ZIP CRC and per-file digests verified. Bounded scans found
zero generated48-hex credential-shaped values or nonempty secret-like XML properties;
this is not a universal secret-detection guarantee. No merge, main change,
workflow dispatch or package publication occurred.

---

# Resumed full qualification after headroom restoration — 2026-10-22

**Default PASS · Full FAILED · Production NO-GO.** The memory prerequisite was
cleared and the approved full container route actually executed. The remaining
full failure is independent JDBC statement cancellation. No production code,
test threshold, required workload, SQL or pacing policy changed in this resume.
Only Model Sync PR #1's existing review branch was used; no merge, main change,
workflow dispatch/bootstrap or package publication occurred.

## Source, commands and original exits

Tested source **`08bd1906e54d6a182a1a7d2c9473fc255f7038fa`**, tree
**`a5f5e717c9fcd3331c139558508c312aff901d60`**, clean change digest
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
The source stayed frozen through execution, collection and build provenance.
This subsequent documentation/evidence commit is not claimed as the full-tested source.

| Execution | Identity | Command / actual result |
|---|---|---|
| Fresh local default | `2bb7a10f-29a9-4b30-9313-48aa2d1960d9` | `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify`; exit0,65.559s |
| Full | `e88fc1de-755e-470c-a7f7-15c3c7a4f13d`; container attempt `3dabb75d-761d-427a-88f7-5126042f4b90` | `python -u scripts/full-oracle-container.py`; Maven1/wrapper1/supervisor1, collector0;7698.963s including infrastructure/cleanup |
| Wrapper's full Maven subprocess | same full identity | `mvnw.cmd -B -ntp -s <repo>/.mvn/qualification-central.settings.xml -fae -Pfull clean verify`; exit1 |
| Recorded default execution audit | same archived default identity | `python scripts/check-default-log.py <archived-default-log>`; exit0,102 plugin goals, none expensive |
| Offline contracts after Maven | same clean08bd190 source | `C:/Program Files/Git/bin/bash.exe scripts/test-triggers.sh`; exit0,86 core/43 trigger assertions; not extra JUnit tests |

Full/Oracle execution set only the invocation's
`MODELSYNC_ORACLE_CONTAINER=EXPLICIT_DISPOSABLE` and
`MODELSYNC_FULL_ENV_ACK=DISPOSABLE_NONPRODUCTION`, with
`MODELSYNC_MAVEN_SETTINGS=.mvn/qualification-central.settings.xml`. External
Oracle credential variables were cleared. Settings SHA256:
`ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`.
Public Central was explicitly selected for this project; no user/global settings,
TLS, DNS or organization controls were changed.

**Default:**117 JUnit tests/25 XML, zero failures/errors/skips;61 Python guards
(30.302s); architecture/docs/lifecycle checks passed. **Full:**119 Surefire tests/27
XML, zero failures/errors/skips;45 Oracle Failsafe tests/11 XML, **one failure**,
zero errors/skips. Full also ran61 Python guards (30.976s). The extra full unit
tests are in the test-only performance module. Default/full counts are separate.
The default execution audit found no PIT/Gatling/Failsafe goals. `-fae` retained
independent module diagnostics while preserving Maven failure. The full report
gate correctly rejected the failed Oracle suite; collector0 means collection
succeeded, not qualification. No stale, mismatched or malformed reports were
accepted (`rejected=[]`). All declared suites executed; none is labelled NOT_RUN.

## Approved disposable infrastructure and cleanup

Read-only resource admission passed at12:08:10UTC with21.648GiB available against
the unchanged6GiB Oracle +8GiB build +4GiB reserve planning budget. C:123.364GiB,
D:1070.053GiB were free. The previous resource-blocked attempt remains unchanged
below; its missing execution is not retroactively converted into this result.

Pinned image:
`ghcr.io/gvenzl/oracle-free:23.26.3-slim@sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`.
Generated per-run credentials, separate `MS_TEST_RUNTIME` / `MS_TEST_MIGRATION`
schemas, synthetic data and a new labelled volume were used. The owned container
`msq-3de5da8e40a9` independently showed6442450944B memory/4000000000 NanoCPUs
and only `127.0.0.1:56068` published for Oracle. Engine:29.8.2,8CPU,
25201704960B memory. No unrelated resources or global Docker settings were changed.

Whole-container CPU/memory/I/O and host memory/disk snapshots are retained in
`session/resources-resume-08bd190.json`. They are sampled infrastructure values,
not peak guarantees or selector/session/cursor work deltas. No observer queries
were added to the running benchmark schema. No resources were increased.

Wrapper cleanup passed; independent inspection at**2026-10-22T14:19:00.767065Z**
confirmed the owned container and `msq-3de5da8e40a9-data` volume absent (both inspect
exits1). Across this shutdown session all **eight** created container/volume pairs
have independent absence receipts; no residual session resource was found.
Test-owned JVMs, pools, schedulers and sockets have their separate case evidence.

Actual tool/runtime versions: Windows11 amd64, Temurin21.0.12.1+1-LTS,
Maven3.9.9, Python3.12.10; Boot4.1.1, Spring7.0.9, Hibernate7.4.5.Final,
Reactor3.8.7, ojdbc11 23.26.3.0.0, Jupiter/Platform6.0.3,
Surefire/Failsafe3.5.3, ArchUnit1.5.0, PIT1.25.9/JUnit provider1.2.3,
Gatling3.13.1/plugin4.11.0. Oracle identifies as AI Database26ai Free,
version23.26.3.0.0. Resolved classpaths/effective POMs and eleven fresh library
JAR hashes are retained; no release artifacts were published.

## Shutdown / I/O outcomes and the remaining failure

The unchanged [failure contract](docs/quality/FAILURE-CONTRACT.md) predates
implementation. Fixture acquisition1000ms, connect1500ms, runtime read2000ms,
statement2s, cooperative transaction2s, JPA lock1000ms and owner socket250ms/fetch300ms
remain distinct. Runtime pool4/observer2; the independent statement probe uses
observer read10000ms. Worker stop tests use one lane/owned one-thread schedulers
and1500ms cooperative/300ms stalled drain bounds. No application-owned datasource
or scheduler defaults were globally reconfigured.

| Full case | Measured outcome | Status |
|---|---|---|
| Graceful owner drain | DRAINED15ms; logical/physical/owner0; one read/one application; owned threads ended | PASS |
| Late owner | TIMED_OUT313ms with logical0/physical1/owner1; after fixture release DRAINED0; one read/zero applications; client/threads ended | PASS |
| Real JPA row-lock wait during shutdown | TIMED_OUT314ms with physical1; after release physical0; old projection/evidence pair; threads ended | PASS |
| Direct Hikari pool exhaustion | acquisition1009ms; pool reused after held connections returned | PASS |
| Owned unavailable/reset endpoint | acquisition1008ms; no silent SYN-blackhole claim | PASS |
| Owned login stall | acquisition1008ms; acquisition plus pool close2024ms | PASS |
| Database transport read stall | 2016ms; physical statement ended; pool reused | PASS |
| Owner socket stall | 254ms; physical client0 and endpoint closed | PASS |
| Cooperative transaction deadline | 2114ms; earlier local write rolled back | PASS |
| COMMIT request withheld | uncertain2017ms; fresh old complete pair/LEASED; recovery completed | PASS |
| COMMIT acknowledgement withheld | uncertain2024ms; fresh new complete pair/IDLE; no repeat required | PASS |
| Independent statement/lock cancellation | effective queryTimeout2s; still physically reading at5017ms; `Future.get(5s)` raised TimeoutException instead of expected completed execution failure/ORA-01013 | **FAILED** |

`OracleIoIT`:12 executed/one failure,24.268s. The failed probe was an explicit
rollback-only local transaction; its bounded stack snapshot shows the owned
thread in Oracle NIO socket read. The original2s/5s/ORA-01013 gate is unchanged.
Fresh-state assertions after that failed assertion did not execute and are not
claimed passing. Suite teardown verified owned executor/pool termination.
Passing socket-read timeout or cooperative transaction expiry does not qualify
the failed independent statement deadline. This remains a concrete release
blocker requiring a supported repair/configuration and fresh Oracle evidence.
No driver/platform change or cancellation root cause is inferred from this run.

These tests prove controlled fixture acquisition/reuse and physical shutdown
outcomes, not saturated-worker pool/thread exhaustion for arbitrary hosts.
Reactive cancellation is explicitly distinct from physical I/O completion;
arbitrary async owners still need a transport-release contract.

## Real process-crash recovery and retained atomicity

All six `OracleRecoveryIT` cases passed in63.011s through actual starter/JPA/JDBC/
`JpaTransactionManager` configuration. Only acknowledged test-owned child JVMs
were forcibly terminated; Oracle stayed available. Fresh connections and fresh
recovery JVMs established committed outcomes after real5s lease expiry.

| Fault point | Fresh post-crash pair/status | Crashed/recovery owner reads* | Recovery ms |
|---|---|---:|---:|
| Claim committed | before/before, LEASED | 0/1 | 8510 |
| Owner responded | before/before, LEASED | 1/1 | 8431 |
| Projection flushed before COMMIT | before/before, LEASED | 1/1 | 8235 |
| Completion committed before caller success | after/after, IDLE | 1/0 | 0 (none needed) |
| Owner responded + newer request | before/before, generation3 retained | 1/1 | 8491 |
| Owner responded + newer request/local revision | local-edit/before, generation3/local marker retained | 1/1 | 8376 |

*Initial setup reads excluded. Five crashed reads plus five recovery reads,
six final successful subject rows and five recovery applications are distinct
counts. All obsolete-token completions were rejected; recovery reached IDLE,
with no partial projection/success-state commits and local-version/owned-field
preservation checked. Five kill confirmations were below1ms timer resolution;
one took89ms. Owned processes exited and pools closed. These are headless public
coordinator JVMs, not continuous-Reactor pod-crash or multi-process contention proof.
All seven retained starter JDBC/JPA atomicity cases passed again in13.319s.

## Actual worker: ten phases PASS

The existing four-lane auto-configured Reactor worker/JPA/JDBC measurement passed
all ten phases in79.547s, pool8/persistence4/owner8/queue64, owner spacing20ms and
latency5ms. Fixed poll/productive250ms versus productive5ms/idle250–1000ms remained
unchanged. Both productive phases completed160 measured syncs/160 owner calls:
**17260ms fixed /9737ms bounded pacing**, within the unchanged20s gate.

Warmed empty polling14.985/3.987 per second and native logical gets3549.451/992.524
per second remain distinct from productive throughput and DB work. All-future
polling3.997/s with zero owner calls/permits. Wakeup upper bounds: future due375ms,
local urgent875ms, independent-process urgent1576ms, expired lease839ms,
other provider31ms, cooldown expiry771ms. All met the existing local2500ms budget.
Ten physical-session intervals were VALID; no failed-interval delta was invented.
Callbacks, native sessions and cursor counters remain separate in the raw evidence.

The earlier full20s backlog failure and later scoped diagnostic PASS remain
separate. This passing repeat establishes this run's gate, not its earlier root
cause, a universal speedup or production SLO. Accepted Q4 pacing/SQL/cursor work
was not redesigned.

## Other full results

PIT **160/162 killed=98.765432%**,8s,394 internal invocations (not extra JUnit
tests), unchanged eight-class behavioral scope and85% killed/all-generated gate.
KILLED160/SURVIVED1/NO_COVERAGE1; all other mutation statuses0. XML/HTML and exact
scope/effective configuration are retained. Remaining `positive(maxAge)` removal
retains the acceptance-equivalence argument documented below; exception ordering/
messages are not claimed equivalent. `SyncCoordinator.options` remains uncovered.
Both remain in the denominator, with maintainer review open. Shutdown code is
outside that unchanged core mutation scope; no extra mutation-strength claim follows.

Six in-memory semantic Gatling scenarios passed,367 requests/zero HTTP errors:

| Scenario | Requests | HTTP p50/p95/p99 ms |
|---|---:|---:|
| Fresh visits | 40 | 70/162/163 |
| Hot stale key | 100 | 218/225/225 |
| Unsafe values | 20 | 61/62/62 |
| Event/job/visit | 4 | 2/24/24 |
| Cooldown/recovery | 3 | 5/25/25 |
| Leases/ledger | 200 | 249/275/276 |

The leases harness recorded96 completed rows/96 owner entries from200 accepted
job requests. Other scenarios retain their own projection/ledger assertions.
These are in-memory semantics and HTTP latency, not Oracle capacity or completed
sync counts inferred from HTTP acceptance.

Oracle capacity:1,000,000 intents (100k due/900k scheduled future), four direct
`runOne` loops/5ms sleeps, pool8, owner spacing20ms/latency5 or25ms. Seed6032657ms;
warmup922ms/10 completions; measurement11554ms/**200 completions/220 physical
owner calls**,17.310 completed syncs/s; total210 completions/230 calls.13 synthetic
429s/7 timeouts.225 ledger attempts/5 quota denials/1969 empty polls/8507 selection
attempts/9012 selector callbacks are separate counts. Summed native block-work59946
is not a query count or wall-clock share. runOne p50/p95/p99=4/45/75ms; claimed
44/76/79ms; lag6069827/6075353/6075864ms includes the long seed. JVM reported
max heap16944988160B/end-used175254976B, not peak usage. This test-only JDBC
projection/direct-coordinator sample is not actual-Reactor/JPA million-row
capacity, a million completed reconciliations or production economics.

The comparison passed518.608s,100k scale757.298s, eleven selection cases0.785s,
concurrency1.256s, migration3.636s and smoke0.510s. The scale claim took29ms/74
summed work units; seed200300ms/cleanup155301ms/zero rows remaining. A ledger claim
is not provider admission. Plans, individual counters/waits, phase costs and
original cursor-attribution corrections are retained. Same caps/workloads do not
make these independent runs a controlled wall-clock speedup comparison.

## Evidence, history and remaining gates

[Raw reports/index](validation/qualification/2026-10-22-shutdown-recovery-rerun/README.md):
ZIP SHA256 `7ce24ee9711e023d5000f0d3ff76b5bad0ad2d71bc4417cfa1b8472631d9db34`,
7,102,974 bytes/1,972 files/25 components. Per-attempt hashes and ZIP CRC passed;
bounded disclosure patterns found zero matches. Original binary Gatling logs and
ICO assets retain their bytes with narrow pinned-header handling; the packaging
decoder failure/correction is recorded. No JAR/class binaries are included.
Each component keeps its own source/run/status; earlier counts are not added.
The run-scoped `target/qm/QM.md` and machine evidence, original failed log/exit,
343 fresh full files, XML/HTML, fault checkpoints/process logs, resources, eleven
library hashes and independent cleanup are archived. Default CI for tested08bd190
[passed](https://github.com/Tobia5/model-sync-starter/actions/runs/39763458161).
Full GitHub CI was NOT_RUN; this was the approved local route.

The memory prerequisite is closed for this attempt. Independent statement
cancellation and passing overall full qualification remain open. Existing
saturated-worker/shared-host recovery, racing inserts/multi-process contention,
arbitrary transport/host configurations, real provider retry/fanout budgets,
operations/security/retention and consumer/supply-chain gates remain open.
All R01–R13 parents remain open. First scope stays known-ID owner-to-Oracle/JPA;
inventory/brokers/vendor connectors/XA/remote writeback are not added.

The complete previous08bd190 report follows verbatim (canonical Git blob SHA256
`498310ada2c648c804998424673f1618d9818cdc0632e71a1342725b5cdbabff`,60187bytes).
It retains the earlier failed full, resource-blocked attempt and accepted Q4
report. All five preceding evidence ZIPs, including full-tested`fad6874`'s archive,
remain unchanged. Their historical outcomes are not reattributed to this run.

## Preserved preceding report


---

# Shutdown, database waits and process-recovery qualification — 2026-10-22

**Default PASS · Completed full attempt FAILED · Final-source full rerun BLOCKED · Production NO-GO.**

Only Model Sync PR #1 was changed, on `review/layered-refactor-qm-20261020`.
The accepted `fad6874` worker/SQL/atomicity evidence is unchanged. Its complete
previous root report is retained verbatim below. No main change, merge,
package publication, vendor connector or bulk-owner feature occurred.

## Exact source and attempts

| Check | Source / tree | Run / outcome |
|---|---|---|
| Final code default | `8830c3f6d2ac8483c35309e747e896131c64fcea` / `10cb9835a8f925f9e45a2a437d9f637ee5668ca2` | `47311f17-5b2c-409b-ab19-8b9340fc8af5`, exit0, 88.439s, PASS |
| Completed full | `3564410ad8e884509cecb0e530a8e3c16473eb29` / `d0af02e4117463eae97a25108b537eb9650fd577` | `d0500d1e-030b-425e-9b49-a35e214f7598`, attempt `90354641-cbe9-4750-abd3-1022c9816ce4`, Maven1/wrapper1/supervisor1/collector0, 7430.627s, FAILED |
| Scoped actual-worker diagnostic | `3564410` plus change digest `5e5ba7f20198dbf780748b619e42f67bef2708d827b5802bff42bc12df824da5` | `778da5e2-47b2-430d-840f-8b7b15b5ff6b`, exit0, 276.175s including setup/cleanup, DIAGNOSTIC_ONLY |
| Final-code full admission | `8830c3f` / `10cb9835a8f925f9e45a2a437d9f637ee5668ca2` | attempt `2b64d09c-a58c-4534-b7ff-668539a5f807`, BLOCKED; wrapper/Maven and all full suites NOT_RUN; no run ID assigned |

Both clean code attempts have the empty change digest
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
The diagnostic's only pending change captured failed-backlog endpoints; it was
then committed as `8830c3f`. It is not retrospectively attributed to that SHA.
There were no production changes between the completed full and final code.
This later report/evidence commit was not the source of either Maven build.

[Raw artifact and index](validation/qualification/2026-10-22-shutdown-recovery/README.md)
retain each attempt's identity, original exit, XML/HTML/JSON and logs. ZIP:
`8c236668f6456fec19c919791c970ab292abe493f9172fd6724bd5f9462db49c`,
4,432,245 bytes, 1,479 files, 22 components. Archive CRC and declared-file hashes
passed. The bounded disclosure scan found zero matches; generated ICO assets
are byte-preserved and scanned separately. This is not a general secret audit.

## Commands, environment and build separation

Approved explicit project settings: `.mvn/qualification-central.settings.xml`,
SHA256 `ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`.
Public Central was used for this personal project; user/global settings were not
changed. The wrapper's Maven subprocess uses the same selected settings.

```text
mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify
  exit 0 (final code)
python -u scripts/full-oracle-container.py
  exit 1 (completed full, source 3564410)
  -> mvnw.cmd -B -ntp -s <repo>/.mvn/qualification-central.settings.xml -fae -Pfull clean verify
python -u scripts/full-oracle-container.py --worker-diagnostic
  exit 0 (scoped diagnostic; no million-row seed; not full qualification)
C:\Program Files\Git\bin\bash.exe scripts/test-triggers.sh
  exit 0 (8830c3f; 86 core + 43 trigger offline assertions, not JUnit counts)
python ../qualification-evidence/shutdown-recovery/capacity_gate.py
  observed PowerShell/tool exit 1; native script's blocked exit definition 2
  final full wrapper/Maven NOT INVOKED after failed resource admission
```

Full/diagnostic invocations alone set `MODELSYNC_ORACLE_CONTAINER=EXPLICIT_DISPOSABLE`
and `MODELSYNC_FULL_ENV_ACK=DISPOSABLE_NONPRODUCTION`, with the project-settings
selection. External Oracle variables were cleared. Generated per-run credentials,
synthetic data and separate `MS_TEST_RUNTIME` / `MS_TEST_MIGRATION` schemas were used.
Pinned image: `ghcr.io/gvenzl/oracle-free:23.26.3-slim@sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`.
Only loopback ports were published. Oracle caps remained **6 GiB / 4 CPU**.

Actual versions: Windows11 amd64, Temurin21.0.12.1+1-LTS, Maven3.9.9,
Python3.12.10, Docker29.8.2; Boot4.1.1, Spring7.0.9, Hibernate7.4.5.Final,
Reactor3.8.7, ojdbc11 23.26.3.0.0, Jupiter/Platform6.0.3,
Surefire/Failsafe3.5.3, ArchUnit1.5.0, PIT1.25.9/JUnit provider1.2.3,
Gatling3.13.1/plugin4.11.0. The database identifies as Oracle AI Database26ai
Free, version23.26.3.0.0. No platform change was made to obtain green tests.

Final default: **117 JUnit tests /25 XML, zero failures/errors/skips**, 61 Python
guard tests, architecture/docs/effective-lifecycle audits passed. Recorded default
execution has102 goals and no PIT/Gatling/Failsafe. Expensive execution stays
explicit/full-only. [Default CI39761845131](https://github.com/Tobia5/model-sync-starter/actions/runs/39761845131)
passed for PR head8830c3f; CI tested merge source`9f18eb58dcbb1184355dc9358a226593d5ee3bd4`,
whose tree is exactly`10cb9835a8f925f9e45a2a437d9f637ee5668ca2`.
Its QM artifact12418985741 was downloaded and checked separately. Full GitHub CI
was NOT_RUN; no default-branch workflow bootstrap or protection bypass occurred.

## Failure contract and implementation

[Failure contract](docs/quality/FAILURE-CONTRACT.md) was committed in`ebeb615`
before implementation. The worker closes new claim admission, allows bounded
drain (default5s, configurable positive bound at most5min), then cancels remaining
subscriptions. `WorkerShutdown` distinguishes DRAINED/TIMED_OUT and records
logical work, tracked physical operations and owner operations separately.
Cancellation fences queued callbacks; physical owner capacity is retained until
the actual callable/stage returns. Restart is refused while prior work remains.
The coordinator's clock supplies capacity deferrals outside the lifecycle monitor.
No DB operation is performed while that monitor is held.

Existing lane pacing, SQL, lease/generation/local-version rules and transaction
ordering were retained. No application-owned datasource, global scheduler or
unrelated process/container was reconfigured. An arbitrary third-party future
still needs a physical transport-release contract: stage completion alone cannot
prove hidden HTTP retries or sockets have ended.

## Independent-JVM process recovery: six PASS in the completed full

Tests use actual starter configuration, JPA projection, JDBC ledger and enlisted
`JpaTransactionManager`. A child acknowledges an exact fault point, PID, work ID,
token and generation; only that owned JVM is forcibly terminated. Oracle remains
available. Fresh connections inspect committed state and fresh JVMs recover
uncommitted cases after the real5s lease expiry. These are headless public
coordinator processes, not continuous-Reactor child-process crash tests.

| Acknowledged fault | Fresh committed outcome after kill | Owner reads crashed/recovery* | Recovery ms |
|---|---|---:|---:|
| Durable claim committed | old complete pair; LEASED | 0/1 | 8424 |
| Owner responded, before result transaction | old complete pair; LEASED | 1/1 | 8531 |
| Real projection flush, before COMMIT | old complete pair; LEASED | 1/1 | 8409 |
| Completion committed, before caller success | new complete pair; IDLE | 1/0 | 0 (no recovery needed) |
| Owner responded + newer request | old complete pair; generation3 preserved | 1/1 | 8464 |
| Owner responded + newer request/local revision | local edit and old evidence retained; generation3/local marker preserved | 1/1 | 8237 |

*Initial setup reads are excluded. Across six cases: five child reads plus five
recovery reads, six final successful subject rows and five recovery applications.
These are not ten successful commits. Each obsolete token was rejected; fresh
recovery reached IDLE without partial projection/success-state commits. Kill
confirmation was below1ms at timer resolution for five cases and69ms for one;
all owned processes exited. The suite took64.32s and all owned pools closed.
At-least-once reads, rather than exactly-once remote I/O, are the documented model.

## Bounded I/O and physical cleanup: eleven PASS, one FAILED

The fixture separates acquisition1000ms, connect1500ms, read2000ms, statement2s,
cooperative transaction2s, JPA lock1000ms and owner socket250ms/fetch300ms.
The independent statement probe deliberately uses a separate10000ms read timeout.
No fetch-timeout result substitutes for a database deadline.

| Case | Measured result in full3564410 | Status |
|---|---|---|
| Graceful controlled owner | DRAINED13ms; logical/physical/owner0; one read/one application; sockets and owned threads ended | PASS |
| Late noncooperative owner | first stop300ms TIMED_OUT: logical0 but physical1/owner1; after explicit fixture release DRAINED0; one read/zero applications | PASS |
| Real locked JPA work during stop | TIMED_OUT309ms with physical1; after release physical0, old projection/evidence preserved, owned threads terminated | PASS |
| Pool exhaustion/acquisition | 1008ms; fixture pool reusable after held connections returned | PASS |
| Owned unavailable/reset listener | 1006ms acquisition; no silent SYN-blackhole claim | PASS |
| Owned database-login stall | 1010ms acquisition | PASS |
| Database read stall | 2001ms; physical statement ended and pool reused | PASS |
| Owner socket stall | 254ms; controlled client/server resource cleanup | PASS |
| Cooperative transaction expiry | 2115ms; earlier local write rolled back | PASS |
| COMMIT request withheld | caller uncertain2014ms; fresh old complete pair/LEASED; recovery completed | PASS |
| COMMIT acknowledgement withheld | caller uncertain2031ms; fresh new complete pair/IDLE; no repeat required | PASS |
| Independent locked JDBC statement | effective queryTimeout2s, still in Oracle socket read at5001ms; required ORA-01013/bound not met | **FAILED** |

`OracleIoIT`:12 executed, one failure, zero errors/skips,23.963s. Its executor,
pools and worker threads were verified ended. The pool test proves direct fixture
Hikari acquisition/reuse, not saturated-worker end-to-end behavior for arbitrary
hosts. The socket-read case does **not** qualify independent statement cancellation.
Repeated diagnostics also failed that unchanged2s/5s gate, including rollback-only
transactions and fixture-only in-band cancellation. No threshold, error assertion,
driver or workload was weakened. Statement cancellation remains a release blocker.

## Actual-worker timeout investigation

Completed full `OracleWorkerIT` failed while awaiting the160 measured backlog
completions within20s; **the ten warmup completions had succeeded**. The raw report
has a before endpoint and no after endpoint, so no failed-interval throughput or
counter delta is inferred. This historical failure is retained.

Commit8830c3f adds failure-time scheduling/session/state/physical snapshots while
preserving the original assertion. The scoped diagnostic passed all ten phases:
160 measured completions/160 owner calls in16490ms fixed versus9275ms bounded
pacing; warmed empty14.974 versus3.992 polls/s. Local urgent877ms,
independent-process urgent1461ms, future eligibility394ms, expired-lease recovery833ms,
other-provider32ms and cooldown-expiry793ms met the unchanged local budgets.
All ten diagnostic session intervals were VALID. Fixture history/warmup and raw
endpoints are retained. This does not erase the full timeout, establish its cause,
or qualify final8830c3f as full PASS. No SQL/pacing redesign or speedup claim follows.

## Other retained full gates and measurements

Completed full: **119 Surefire /27 XML, zero failures/errors/skips; 45 Oracle
Failsafe /11 XML, two failures, zero errors/skips**. All seven scoped JDBC/JPA
atomicity cases passed again (13.05s), as did eleven selection cases, migration,
concurrency, smoke,100k scale, four-way comparison and the unchanged million-row
workload. Architecture scanned the compiled full code. `-fae` allowed independent
PIT/Gatling modules to finish; Maven remained failed, collector exit0 only means
diagnostics were collected. Required report qualification correctly failed.

PIT: **160/162 killed =98.765432%**,7s,384 internal test invocations (not JUnit
counts), unchanged eight-class scope and85% killed/all-generated gate.
Raw KILLED160/SURVIVED1/NO_COVERAGE1; TIMED_OUT/RUN_ERROR/MEMORY_ERROR/NON_VIABLE0.
XML/HTML, effective configuration and all mutation records are retained.
The surviving removal of`positive(maxAge)` retains the acceptance-equivalence
argument: positive refreshAfter plus refreshAfter<=maxAge rejects nonpositive
maxAge; null still fails comparison. Exception ordering/messages are not claimed
equivalent. `SyncCoordinator.options` remains uncovered. Both remain in the
denominator and maintainer review stays open. New worker shutdown code is outside
this unchanged core mutation scope; no mutation-strength claim is made for it.

Six in-memory semantic Gatling simulations passed,367 HTTP requests/zero errors:

| Scenario | Requests | HTTP p50/p95/p99 ms |
|---|---:|---:|
| Fresh visits | 40 | 71/164/164 |
| Hot stale key | 100 | 203/212/213 |
| Unsafe/missing values | 20 | 59/60/60 |
| Event/job/visit | 4 | 2/24/24 |
| Cooldown/recovery | 3 | 5/24/24 |
| Leases/ledger | 200 | 245/278/284 |

These are synthetic in-memory semantic checks, not Oracle capacity or owner
completion latency. The leases harness recorded92 completed rows and92 owner
entries from200 accepted job requests. Other scenarios use their own projection/
ledger/owner assertions; an accepted HTTP request is not a completed sync.

The separate Oracle coordinator capacity fixture retained1,000,000 seeded intents
(100k due/900k scheduled future), four direct`runOne` loops/5ms sleeps, pool8,
20ms owner spacing and5/25ms synthetic owner latency. Seed6028698ms is separate
from9448ms measurement. Measurement: **200 persisted completions /220 physical
owner calls**,21.169 completions/s;224 ledger attempts,4 quota denials,2796 empty
polls,12224 selector callbacks. Total including warmup:210 completions/230 owner
calls;13 synthetic429s/7 timeouts. Native summed block-work70292 is not a query
count. runOne p50/p95/p99=3/34/59ms; claimed43/65/69ms. Lag6047316/6051476/6051868ms
includes the long seed. Test JVM reported max heap16944988160B/used295105688B.
This is test-only JDBC projection/direct-coordinator capacity, not a million-row
actual-Reactor/JPA qualification or a million completed reconciliations.
The separate100k claim took21ms,43 summed work units; seed131677ms/cleanup134937ms,
zero rows remaining. A ledger claim is not provider admission. Original separate
native/session/cursor counters, plans, waits and attribution corrections remain raw.

## Final-source resource blocker and cleanup

Final full was stopped **before provisioning**, not failed for unavailable Oracle
secrets. Four host-memory samples over48s were17.846/17.774/17.571/17.494GiB,
below the unchanged6GiB Oracle +8GiB build +4GiB reserve planning budget.
The [final read-only recheck](validation/qualification/2026-10-22-shutdown-recovery/final-resource-recheck.json)
at2026-10-22T10:28:38.8439555Z still found16.644GiB and created no resources.
Docker29.8.2 reported8CPU/25201696768B engine memory/zero running containers;
C:126.592GiB and D:1070.066GiB were free. The host budget was not reduced, resources
were not increased and unrelated workloads were not stopped. The resource receipt
has no run ID, no Maven/wrapper exit and no attributed suite reports. Final full
execution is NOT_RUN with qualification BLOCKED. Adequate host headroom and another
complete exact-source full attempt are required; statement failure also remains.

All **seven** containers and matching volumes created by this session (six scoped
diagnostics and one full) were removed by their wrappers and independently
confirmed absent. The completed full resource`msq-1b7391d4318b`/matching`-data`
volume was absent at2026-10-22T10:05:11.870749Z; final diagnostic
`msq-b32786a6a70a`/matching volume at10:16:13.462103Z. No residual session resource
was found. The blocked final attempt created none. No unrelated resources,
credentials or Docker global settings were touched.

## History, scope and remaining gates

Raw evidence preserves initial compile/guard/fixture failures and five failed
process/I/O diagnostics separately. The initial process fixture exposed demo YAML
overriding configuration (including create-drop); the command-line settings fix
has a cheap actual-YAML regression. Other focused fixes cover exact checkpoint
work IDs, owned connection-reset endpoints and rollback-only cancellation probes.
Diagnostic sources/runs/exits remain in their manifests; none is promoted to full.
The packaging decoder failure and narrow ICO correction are also recorded.

Completed leaves: acknowledged independent-JVM crash/reclaim/fencing cases;
newer generation/local revision preservation; controlled graceful/late-owner and
locked-JPA stop outcomes; fixture acquisition/read/owner/transaction bounds; fresh
uncertain-COMMIT outcomes and owned cleanup. **Independent statement deadline,
final exact-source full requalification and every incomplete parent gate stay open.**
Other open criteria include saturated-worker pool recovery, racing inserts and
multi-process claim contention, arbitrary host/transport configurations, silent
network partitions/streaming peers, multi-pod availability/SLOs, real owner
retry/fanout budgets, operational/security/retention controls and consumer/supply
chain qualification. Inventory/brokers/vendor connectors/XA/writeback remain
outside the first known-ID owner-to-Oracle/JPA scope. See [TODO](TODO.md).

## Preserved accepted Q4 report (verbatim)

The following prior report bytes are unchanged (SHA256
`a715b6f401f87dffae399f631526a5a1e4d01eaaa921b1306c5ff2524a58b7f5`).
Its PASS applies to its named source, not the failed or blocked attempts above.


---

# Model Sync qualification evidence

**2026-10-22. Default: PASS. Configured local full: PASS. Production: NO-GO.**

Tested code **`fad6874c9fa0e0a16ed7d542cace11ea89a991e1`**; tree **`a530a260738510da84ed7f68fd26a8a75b803d67`**; clean change digest
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`. A later report/documentation commit is not claimed as full-tested.

**First supported scope:** known-ID owner-to-Oracle/JPA reconciliation in one correctly enlisted
datasource/`JpaTransactionManager`, with application-supplied detached mappings, owner reader,
authorization and provider policy. All R01-R13 parent gates remain open. No vendor connector,
inventory, bulk-owner feature, main change, merge or package publication is included.

## Current commands and identity

| Lane | Actual command / identity | Exit / measured outcome |
|---|---|---|
| Local default | `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify`; run `4285d942-bb2c-4cd0-b1f0-6ba62e9b1203` | **0 / PASS**, 57.605s supervisor elapsed; 111 JUnit tests, zero failures/errors/skips; 57 Python guards |
| Targeted worker diagnostic | `python -u scripts/full-oracle-container.py --worker-diagnostic`; run `5e90ca7c-540f-411f-965d-6108c76b6d2c` | **0 / diagnostic PASS**, 156.583s; one JUnit test/ten phases; full **NOT_RUN** for that attempt |
| Full | `python -u scripts/full-oracle-container.py`; run `1afffb7d-52ca-4d4a-8857-23c3cefb8f4c`; attempt `d61f7d92-b0e8-4604-8823-a817a71f6ee7` | **Maven0 / wrapper0 / supervisor0 / collector0 / PASS**, wrapper7763.45s, supervisor7764.018s including provisioning/cleanup |
| Default CI | [39682007724](https://github.com/Tobia5/model-sync-starter/actions/runs/39682007724), run `468f0059-d144-4b92-8247-feed6eb6f082` | **PASS**, 111 JUnit; actual merge-test SHA `33e114c25bd42eeaaa0214c6b34d294259955739`, identical tested tree |
| Offline current-code contracts | Git Bash `bash scripts/test-triggers.sh`, sequentially after full | **0 / PASS**, 86 core / 43 trigger assertions; not additional JUnit tests |

The full wrapper invoked `mvnw.cmd -B -ntp -s <repository>/.mvn/qualification-central.settings.xml
-Pfull clean verify`. Invocation-only environment: `MODELSYNC_MAVEN_SETTINGS=.mvn/qualification-central.settings.xml`,
`MODELSYNC_ORACLE_CONTAINER=EXPLICIT_DISPOSABLE`, `MODELSYNC_FULL_ENV_ACK=DISPOSABLE_NONPRODUCTION`.
Six external Oracle connection variables were cleared. Settings SHA-256
`ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`; user/global settings, DNS and TLS
were unchanged. Original Windows Cp1252 log bytes and the normalized UTF8 collector log are retained.

Default recorded 102 plugin goals, none PIT/Gatling/Failsafe. Full retained base checks and produced
**113 Surefire tests/23 XML +27 Oracle tests/9 XML**, zero failures/errors/skips, six Gatling
simulations and a passing final evidence gate. No reports were rejected, required tests filtered,
thresholds lowered or workloads reduced. Full GitHub CI is **NOT_RUN**: the workflow registry
exposes the default workflow, while the full workflow remains branch-only/unregistered on main.
No bootstrap or dispatch was performed. Approved local disposable execution supplied this result.

## Actual continuous worker: configuration and measurements

`OracleWorkerIT` starts the actual auto-configured `ReactorSyncWorker` SmartLifecycle bean and
coordinator with real JPA projection/JDBC ledger+quota implementations, instrumented through
delegating test ports. The configured manager is `JpaTransactionManager` on the same datasource.
Four lanes/persistence threads; eight owner threads; queue 64/thread; Hikari 8/maxLifetime 0;
main-provider spacing 20ms; synthetic owner latency 5ms, one controlled fetch per grant.
These stub entries are not measurements of real provider HTTP/fanout. Ten actual-worker warmup
completions and 160 measured completions per mode; fixed mode first, fresh host/pool per mode;
no global cache flush, retained table/index history. Observation SQL is included in native work.

Auto-configured pacing now waits 5ms after a fetched/application attempt, and 250, 500, then at most
1000ms after empty, prepare-only/deferred or failed ticks. Fetch resets only that lane. Positive
delays, four-lane capacity, authoritative distributed admission and persisted failure backoff remain.
There is no next-due cache. Existing five-argument construction retains its fixed-delay behavior.
`WorkerActivity` counts scheduling outcomes, not successful committed synchronizations; deferred
includes RetryLater and prepare-only/already-fresh completion. Quota decisions and persisted
APPLIED outcomes are counted separately. New pacing is unit/application-tested, outside the
unchanged eight-class core PIT scope.

| Actual worker phase | ms | Polls / empty / deferred | Callbacks | Grants / denials / stub calls | Committed APPLIED | Completed/s | Native logical gets / physical reads |
|---|---:|---|---:|---|---:|---:|---|
| fixed-productive | 18061 | 254 / 94 / 0 | 1319 | 160 / 0 / 160 | 160 | 8.859 | 364697 / 0 |
| bounded-productive | 9467 | 208 / 48 / 0 | 963 | 160 / 0 / 160 | 160 | 16.901 | 211129 / 0 |
| fixed-empty | 4000 | 60 / 60 / 0 | 360 | 0 / 0 / 0 | 0 | 0.000 | 13972 / 0 |
| bounded-empty | 4012 | 16 / 16 / 0 | 128 | 0 / 0 / 0 | 0 | 0.000 | 4007 / 0 |
| bounded-future | 4011 | 16 / 16 / 0 | 96 | 0 / 0 / 0 | 0 | 0.000 | 3863 / 0 |

Fixed productive configuration: 250ms after every tick. Bounded productive configuration: 5ms
after fetch, idle cap 1000ms. Both completed 160; elapsed 18061→9467ms within this single controlled
sample. Native total logical gets364697→211129, while logical gets/s increased20192.5→22301.6
with productive throughput. This does not promise a uniform resource-rate reduction or production SLO.

After3s idle warmup, the4s empty measurement changed15.00→3.99 polls/s,90.00→31.90 selector
callbacks/s and3493.00→998.75 native logical gets/s. All-future(1000 rows):3.99 polls/s,
23.93 callbacks/s,963.10 logical gets/s. All three idle windows had zero grants, denials,
stub calls and physical reads. Per-poll cost did not uniformly improve; selection SQL is unchanged.

Predeclared local budgets: warmed adaptive idle≤6*t_seconds+4 ticks; control≤18*t_seconds+4;
selector callbacks≤9*ticks+36; adaptive idle≤100000 logical gets/s; wake probes≤2500ms.
**Documentation correction:** there are FOUR priorities. The maximum empty path count with two
bindings can be12 callbacks/tick. The unchanged9*ticks+36 assertion is an aggregate window
resource allowance, not a mathematical per-tick bound. The earlier three-priority explanation was
incorrect. No numbers/gates were changed after observing results. Client fetch size/max rows,
callback counts and this small resource budget do not prove constant database work.

| Wake/recovery probe | Recorded ms / outcome |
|---|---|
| Future WAITING becomes due without another enqueue | observed completion upper374 |
| New local urgent work | conservative completion bracket847–859 |
| Independent JVM enqueues through public WorkStore | exit0; bracket959–1571; phase1879 includes process startup |
| Expired lease during main-provider cooldown | observed recovery upper838; returned to WAITING with no main-owner call |
| Independent provider during cooldown | one committed APPLIED outcome; application reconciliation timestamp lag33 |
| Cooldown expires | observed completion upper770 after eligibility; two main-provider calls/two APPLIED outcomes |

The other-provider33ms value is MS_STATE's application timestamp before COMMIT, not exact fresh-read
latency; a fresh committed read verifies its outcome. Other wake fields use observer timestamps;
cross-process commit bracketing conservatively includes connection/transaction and observation time.
These are isolated probes, not p50/p95/p99 distributions. Polling awaits are not database statement
timeouts. All ten phases had identical eight SID/serial pairs at native endpoints, zero benign-probe
failures and zero measured cancellations. After snapshots precede cancellation; fixtures stop in a
natural idle gap. This does not qualify shutdown during I/O, hung adapters or general production drain.
Native worker counters exclude the exited enqueue process's own database work. Scheduling and
native endpoints are sequential observations with declared boundary allowances.

## Coordinator workload remains a separate measurement

The existing benchmark still uses four direct `runOne` loops with5ms sleep, JDBC projection,
pool8, owner spacing20ms,5/25ms latency,429 every17th call and timeout every29th. It is not the
auto-configured worker/JPA probe. One million seeded intents,100k due/900k future,100 hot requests;
230-call target/150-completion floor retained. Seed6264795ms; ten warmup calls/completions913ms;
measurement18167ms; owned teardown12372ms, zero ledger rows, FK restored/validated and orphan
rejection verified against the matching2137ms one-record preflight.

Measured224 ledger attempts /220 grants+stub entries /4 denials /200 persisted completions,
**11.009 completed/s**; total230 entries/210 projections/completions with warmup,13 rate limits,
7 synthetic timeouts. Polls3062/empty2838; store selections11968/empty11744; completed selector
callbacks12457. Native51646 consistent+19201 current=70847 logical gets,239 physical reads,
18620 executions,19220 parses,3038256 redo bytes,3747 commits,53 sorted rows. The legacy
`db_session_work=71049` has different endpoints/includes physical reads; it is not the logical sum.
Claimed p50/p95/p99=44/75/80ms; empty4/4/5ms; overall4/43/64ms. Lag6289465/6301576/6302087ms
includes the long seed. **One million seeds are not one million completed reconciliations.**

Accepted ede coordinator sample:200 measured completions/11096ms=18.025/s; current200/18167ms.
Core selector/admission/coordinator code and Oracle caps are unchanged. Seed also slowed from
4336952 to6264795ms; a bounded liveness snapshot observed checkpoint-incomplete redo switching.
Do not attribute this cross-run variation to Reactor pacing or infer wall-clock share from logical gets.
Within the current100k forward/reverse comparison, all eight cases passed: combined native gets/
completion293.675/285.865 versus baseline154220.378/198739.239, denials5/4 versus83/172;
selector-only433.765/531.885 gets with507/666 denials; admission-only112659.800/112652.880 gets,
zero denials. Comparison594.4s. Eight scale populations/48 paired legacy-selected probes passed,
including gathered statistics; eleven selection cases and three concurrency cases passed. Plans,
statistics, waits, physical reads and phase costs remain in raw JSON, without reopening accepted SQL.

## Cursor attribution and evidence integrity

Full loaded identity (SQL/child/address/load time/load count) plus monotonic observed counters is
required for a delta. Reloads, resets, missing-before and disappearing children retain original
snapshots with UNATTRIBUTABLE/null deltas. An unchanged continuous child has a valid zero delta.
Six cheap measurement tests cover retained/reset reload counts, regressions and disappearance;
Python guards reject invented ambiguous counts. LAST-plan discovery is separately labelled when
its execution window is unattributable; it is not summed as current SQL executions.

Across60 non-overlapping declared coordinator/scale/comparison windows: **1116 VALID interval
records /42 UNATTRIBUTABLE** (20 load changes,20 missing-before,2 disappeared; no live reset in
this run). These are selected statement-family interval records, including valid zeros, not distinct
cursors or execution totals and not all worker SQL. Callback, native-session and cursor counters
remain separate. The historical invalid reload sum and documented correction remain unchanged.

## Retained safety, mutation and load evidence

Seven `OracleAtomicityIT` cases passed in12.14s through the actual starter/JPA/JDBC manager;
case source is unchanged from the accepted milestone. Fresh reads verified success, rollback after
flush, ledger failure, beforeCommit, physical Oracle commit rejection, local-version and stale-lease
checks. Six portable H2 cases and wrong-datasource startup rejection remain in default.

PIT 1.25.9/JUnit provider 1.2.3 ran with Jupiter/Platform 6.0.3: **160/162 killed=98.765432%**,
7s,415 internal test invocations (not JUnit counts). Raw KILLED160/SURVIVED1/NO_COVERAGE1;
timeouts/run errors/memory errors/nonviable zero; unchanged85% killed/all-generated gate and
eight behavioral classes, all22 provider-scheduler mutations killed. Remaining FreshnessPolicy
positive(maxAge) removal has the unchanged acceptance-equivalence argument (positive refreshAfter
and ordering reject invalid maxAge; exception order/messages not equivalent). SyncCoordinator.options
remains uncovered. Both stay in denominator, no new exclusions, maintainer review open. Java mutations
do not verify Oracle SQL.

Six in-memory Gatling simulations passed:367 HTTP requests,zero errors,one passing assertion XML
each. They are semantic checks, not Oracle capacity. Warmup0, max heap1GiB, eight handlers/backlog256.

| Scenario | HTTP requests | HTTP p50/p95/p99 ms | Ledger / stub entries / completed rows | Measurement+drain ms |
|---|---:|---|---|---:|
| fresh | 40 | 75/163/163 | 1 / 1 / 0 | 657 |
| hot | 100 | 192/199/200 | 1 / 1 / 0 | 688 |
| unsafe | 20 | 57/58/58 | 1 / 0 / 0 | 1189 |
| event | 4 | 2/22/22 | 1 / 2 / 0 | 486 |
| cooldown | 3 | 5/23/23 | 1 / 2 / 0 | 429 |
| leases | 200 | 257/281/285 | 200 / 82 / 82 | 807 |

Fresh's one owner entry is initialization, not a fetch caused by fresh visits. HTTP acceptance,
owner entries and committed synchronization outcomes remain different quantities.

## Environment, cleanup and preserved attempts

Temurin21.0.12.1 / Maven3.9.9 / Python3.12.10, Windows; Boot4.1.1/Spring7.0.9/Hibernate7.4.5.Final/
H2 2.4.240; Surefire-Failsafe3.5.3/ArchUnit1.5.0/Gatling3.13.1 plugin4.11.0; Oracle26ai Free/
ojdbc11 23.26.3.0.0. Pinned image digest
`6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`.
Container6GiB/4CPU; effective FreeDB2CPU, SGA1610612736/PGA536870912 bytes, writer1/two10MiB
redo groups, unchanged from ede. Docker29.8.2/8CPU/25201700864 bytes engine RAM. Preflight
25.555GiB free host RAM, C132.749/D1070.142GiB free; planned6Oracle+8build+4reserve. Point
samples are not peak proof. Workload JVM max16944988160/end-used270380992 bytes,16 visible CPUs;
pool8/lifetime0 only for bounded measurements. No resource increase or global Docker change.

Full owned container `msq-1927b79e7766`/volume `msq-1927b79e7766-data` were removed and
independently absent (inspect exits1 with specific not-found responses). Successful/failed diagnostic
containers/volumes were also independently absent. No residual session resources; public caches
retained. All512 pre-existing stopped containers and unrelated resources were untouched.

| Preserved attempt | Actual disposition |
|---|---|
| Dirty development CLI/StepVerifier/enum-switch-helper checks | FAILED; separate original logs. Unbound/dirty data never qualifies current source |
| 3a9d641 blocked diagnostic preflight, wrapper014b97a7 | BLOCKED/Oracle NOT_RUN; Cp1252 log decode before provisioning, wrapper2; old supervisor collector then exited1 on absent marker; original exits retained |
| 3a9d641 diagnostic run dfe0b23e | FAILED, one executed JUnit test; quota RetryLater activity misclassified and stop interrupted Oracle reads. Native comparisons unqualified, partial results retained; cleanup PASS |
| 9c83934 default | FAILED Python guard positive-fixture error before marker/JUnit; later suites NOT_RUN; corrected in fad6874 |
| fad6874 default / diagnostic / current full | Separately source/run-bound PASS results above; no historical evidence attributed to them |

## Review, artifact and remaining gates

Responsibility review: ReactorSyncWorker owns bounded async orchestration/activity; WorkerPacing
owns per-lane delays; core planning, admission, result application and failure policy remain unchanged.
Counter classification is test-only. Constructor injection and inward dependencies remain; actual
compiled-output architecture checks and no-hand-written-nested-types rules passed. This review is
not independent security approval or proof of every SOLID property.

[Raw evidence](validation/qualification/2026-10-22-worker-pacing/README.md):
**3,607,197 bytes /758 files /13 components**;
SHA-256 `af3434238eafdd9f3064012a5d41bf7012c19499e02146fadff7072129afe7aa`. Per-file hashes/ZIP CRC and756-input credential-shaped/sensitive-XML
scan passed with zero findings;11 library JAR hashes only, no binaries. Run-scoped `target/qm/QM.md`
and all raw reports are archived. The root report preserves the exact previously published ede
report below, and all three earlier ZIP hashes were verified unchanged.

**Production remains NO-GO.** Parent gates stay open: independent-process crash/restart/racing
inserts, production DB timeouts, migration interruption/backup/rollback, production-like multi-pod
capacity/freshness SLOs, future-only/index-history work, slow/hung-provider isolation and physical
fanout budgets, shutdown/connection exhaustion, authorization/security/operations, independent
consumers, compatibility/SBOM/supply-chain and retention. Local small-probe budgets and full-profile
PASS do not close these gates. Full CI registration/protection also remains unresolved. Batching,
bulk-owner protocol, vendor connectors and inventory were not added.

---

## Historical ede199a qualification report — preserved verbatim

Everything below is the earlier c21d6da report for its explicitly named source/run, not current results.

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
