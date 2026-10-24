# Verification profiles

### Isolated database NOWAIT prototype

`python scripts/full-oracle-container.py --nowait-diagnostic` runs seven small
real JDBC/Hibernate/JPA prototype tests, including three refusal attempts per
held blocker. It does not select the accepted A/B/C cancellation matrix, full
verification or any large/load/mutation suite. Production defaults are unchanged;
all SQL prototype seams are test-only and the host requires `nowait-oracle`.
See [the audited paths and acceptance conditions](NOWAIT-PERSISTENCE-AUDIT.md).
Cheap exact-SQL/scope/refusal guards compile/run in default. Actual SQL, Oracle54,
server session/transaction observations and rollback/recovery are opt-in evidence,
not promises derived from a JPA hint. This lane retains the small JVM/container
caps and scoped12 GiB planning budget described below.

### Explicit process and I/O fault lane

`python scripts/full-oracle-container.py --cancellation-diagnostic` explicitly selects
six one-row locked-DML probes: automatic query timeout, one cross-thread manual cancel,
and layered `Connection.setNetworkTimeout`, each through direct JDBC and Spring/Hikari.
It retains query 2s/read 10s/observation 5s, with separately configured network 3s for
containment only. Oracle must confirm the exact test-owned blocking session before
manual cancel. The lock stays held through observation and natural read-timeout cleanup;
emergency unlock can never earn cancellation credit. Physical execution, cancel invocation,
rollback/disposal, subsequent committed work and owned-resource termination are separate
measurements. Later cleanup observations do not extend the 5s gate. The diagnostic class
matches neither default Surefire nor full Failsafe patterns and runs only via this explicit
launcher. It does not replace or change `OracleIoIT` or any full gate, seed a large workload,
run PIT/Gatling or qualify full. JUnit success means the diagnostic was collected and checked;
per-probe cancellation/containment outcomes remain explicit in the run-bound JSON.
The cancellation-only JVM is capped at512 MiB heap/128 MiB direct memory. Local
planning reserves6 GiB for Oracle,2 GiB for this small launcher/runtime and4 GiB
host reserve (12 GiB available before provisioning). The prior18 GiB full-pipeline
planning budget, Oracle6 GiB/4 CPU caps and all qualification gates are unchanged;
this diagnostic launches neither PIT/Gatling nor a large workload. Retain every
earlier blocked capacity observation separately.

[The failure contract](FAILURE-CONTRACT.md) precedes this implementation. Default verification
adds shutdown policy, uncancelled async-stage/physical-capacity and configuration regressions.
`OracleRecoveryIT` has six independent-JVM cases: four acknowledged crash points, a newer
request and a concurrent local revision. Only the owned worker JVM is forcibly terminated;
Oracle remains available to fresh observers and a new worker process. `OracleIoIT` has twelve
small lock/pool/transport/transaction/shutdown/uncertain-COMMIT cases. Fixture-owned pools,
loopback relays and synthetic owner sockets provide separate bounds; no host datasource
configuration is changed. Transport payloads and credentials are never recorded.

`python scripts/full-oracle-container.py --recovery-diagnostic` runs just these eighteen tests,
after a successful current-source default build and explicit full preflight. It neither seeds
a million rows nor runs PIT/Gatling; its result is DIAGNOSTIC_ONLY / full NOT_RUN. The complete
full profile retains all prior workloads and additionally requires both suites and fresh,
source/run-bound `oracle-recovery.json` / `oracle-io.json`. Partial failed fault measurements
are collected independently of qualification; missing cases/resource proof cannot qualify.
See QM for actual executions; test implementation alone is not successful evidence.

The full container entry point uses Maven `-fae -Pfull clean verify` with the same explicit
settings. Fail-at-end collects independent modules' evidence after a failure; it never skips
required work or changes a failure into success. The wrapper preserves Maven's failing exit
and the final full gate still rejects any failed/missing suite. Default commands are unchanged.

The full-only local HTTP harness has eight handler threads and a bounded accept backlog of 256 for the largest declared 200-user burst. A 200-socket regression checks connection refusal without running Gatling. The Gatling fork explicitly uses the performance module as its working directory, placing harness JSON beside that module's reports. Qualification checks both harness outcomes and fresh Gatling request counts/assertion XML; HTTP failures cannot be hidden by passing owner/ledger assertions. Failed-run QM retains request counts and response-time percentiles separately from completed synchronizations.

| Command | Required work | Excluded work |
|---|---|---|
| `./mvnw -B -ntp clean verify` | Compile all default reactor modules; JUnit unit/contract and bounded H2 tests; compiled-output ArchUnit; source/POM/docs/report guards | PIT, Gatling, Docker, real Oracle, long soak and external providers |
| `./mvnw -B -ntp -Pfull clean verify` | All default work plus selected PIT, Oracle smoke/migration/concurrency/scale/capacity, six Gatling simulations and raw-evidence gate | Production providers/data; stable package release |

`full` has no implicit activation. Ordinary checks are in the base build; expensive goals are bound inside explicit `full` profiles. `scripts/check-profiles.py` audits the declared lifecycle and its module dependency edges. A current default Maven execution log must show no PIT/Gatling/Failsafe goals. `scripts/full-preflight.py` rejects skip/filter flags and missing disposable Oracle inputs before expensive goals. `scripts/check-full-reports.py` requires nonempty raw evidence newer than the run marker, bound to the source SHA/tree/change digest, and the declared PIT threshold. The report generator collects fresh, run-bound partial results even after a failed build, labels executed failing suites FAILED and unexecuted suites NOT_RUN, and rejects stale or malformed reports. A blocked preflight invalidates the previous run marker. A failed Maven exit stays failed.

The manual full workflow offers `external` and `disposable-container` modes. External mode requires separate, approved `MS_TEST_` runtime and migration schemas with the v2 runtime schema installed; runtime needs read access to Oracle session statistics views. Container mode must be explicitly chosen and requires `MODELSYNC_FULL_ENV_ACK=DISPOSABLE_NONPRODUCTION`. `scripts/full-oracle-container.py` uses a digest-pinned Oracle Free image, generates per-run credentials, creates separate schemas, installs v2 only in the runtime schema, binds port 1521 to loopback, and stops its unique container on every exit. Do not use either mode with production credentials or data. The container test applies and removes only a test-owned projection table after verifying it was absent. Migration DDL remains restricted to the separately acknowledged empty migration schema.

For the approved local qualification of this public personal repository, `.mvn/qualification-central.settings.xml` selects HTTPS Maven Central without credentials when the user-level `kubernetes-dev` mirror is unavailable. Run the local default Maven command with `-s .mvn/qualification-central.settings.xml` and set `MODELSYNC_MAVEN_SETTINGS=.mvn/qualification-central.settings.xml` so nested lifecycle audits and the full container wrapper use the same file. The wrapper accepts only this reviewed project settings path and records its SHA-256 in nonsecret infrastructure evidence. Keep mandatory organization mirrors when an applicable policy requires them; do not edit user or global settings.

Each container attempt creates a new labelled Docker volume for `/opt/oracle/oradata`; it refuses to reuse an existing volume. Cleanup checks the attempt label before removing the container and volume, and records both removal results. Schema SQL is streamed over Docker stdin, with generated passwords redacted from bounded error output. Oracle fixture batches use explicit local transactions, retain their full dataset and checks, and record seed time separately from reconciliation time. The workload writes measured JSON before its assertions so a failing assertion preserves its measurements. The eight-connection test pool has no lifetime retirement during the bounded measurement, keeping its session-counter population stable; this setting is reported and is not a production pool-lifecycle qualification.

Million-row teardown has a separate disposable-container strategy. It requires every explicit acknowledgment, the generated `MS_TEST_RUNTIME` schema and exact loopback URL. A real one-record preflight exercises it before the expensive seed. After worker drain and measurement, it rejects unrelated ledger/state rows and unexpected foreign keys, truncates only the owned ledger/state, restores and validates the single state foreign key in every truncate outcome, then verifies Oracle rejects an orphan insert. The full evidence gate requires matching preflight/teardown strategy, scope and restoration/enforcement proofs. Failures remain failures. Approved external schemas retain binding-scoped DELETE. No shipped adapter uses this helper. Oracle [requires disabling the referencing constraint for parent truncate](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/TRUNCATE-TABLE.html); this teardown restores it before subsequent suites. DDL teardown changes segment/cache history and cannot be compared as row-DELETE performance. Seed, steady-state processing and teardown costs remain distinct; required million-row data, completion assertions, resource limits and all suites remain intact.

PIT 1.25.9 with JUnit 5 plugin 1.2.3 targets freshness and application behavior; the target list is in `model-sync-testkit/pom.xml`. The proposed gate is 85% **killed / all generated** mutations, computed from raw XML; killed, survived, no-coverage, timed-out, and run-error counts remain distinct. Zero mutations fail. The approved local `full` run recorded in [QM](../../QM.md) executed PIT with this checkout's Boot 4.1.1 / JUnit Platform 6.0.3 successfully; its measured score, raw counts and remaining release limits are recorded there.

The six Gatling Java DSL scenarios use an in-memory local harness for semantic checks. They are never described as Oracle capacity evidence. `OracleScaleIT` seeds 100,000 due rows with production `WorkKey.storageId()` values and measures one **ledger claim**, including persisted lease token/generation and `lockValid()`; it does not count a provider admission. `OracleCapacityIT` seeds one million durable rows (100,000 due), coalesces 100 hot requests, and drives the real coordinator, JDBC ledger/quota and a test-only Oracle projection through four workers. An instrumented local owner uses 20 ms shared spacing, 5/25 ms latency and bounded synthetic 429/timeouts. Its report separates attempts, physical owner calls and their measured rate, completed persisted projections and their measured throughput, Oracle session work, run-one latency and reconciliation lag. The 150-completion floor proves that a measured sample completed; it is not a throughput SLO or proof that one million subjects were refreshed. External-provider economics, other-owner isolation and production resource limits remain separate review gates.

### Preserved Q2 transaction and queue diagnostics

**Historical source `1a9187c`:** statements below about unchanged production queries/indexes describe Q2. The current selector/admission changes follow in the next sections, with actual Q3 results in [QM](../../QM.md).

After the five original distributions, the scale fixture repeats all-due, mixed and all-future with freshly gathered fixture table/index statistics (`DBMS_STATS`, no histograms). Statistics gathering has its own time/counter phase outside claim measurements. This distinguishes query/queue cost from missing or stale optimizer statistics without changing production indexes, query hints or policy. Plans require positive execution deltas for the loaded cursor identity (SQL ID, child number/address, load time and load count). Oracle can reuse a child number and reset its execution count after statistics invalidate it. A counter regression within the same loaded identity fails. Oracle `LAST_ANALYZED` is recorded as database-local wall time, without inventing a UTC offset for a `DATE` value.

For development investigation only, `scripts/full-oracle-container.py --diagnostic` provisions the same explicitly acknowledged disposable container and runs the seven mixed-transaction cases followed by the complete 100k distribution diagnostic. It first requires a fresh successful default build, its recorded `target/qm/default-build.log`, and the resolved/compiled test classpath. It executes the root `-Pfull -N initialize` preflight, then a test-only JUnit launcher; the ordinary full command remains `-Pfull clean verify`. Diagnostic JSON and infrastructure evidence explicitly say `DIAGNOSTIC_ONLY` / full `NOT_RUN`. These selected tests cannot substitute for PIT, the million-row workload, Gatling or a full qualification.

The full-only sample `OracleAtomicityIT` adds seven tests through the actual starter auto-configuration, `JpaTransactionManager`, `JpaProjectionStore` and JDBC ledger. It checks normal completion, rollback after JPA flush, a database constraint error during ledger completion, failure in Spring `beforeCommit`, an Oracle deferred-unique failure at physical commit, a changed local revision and an obsolete lease. Fresh independent JDBC transactions inspect committed data. Separately committed retry metadata is permitted; successful projection/state may never partially commit. The default build runs the six portable cases on H2 and a negative datasource-alignment startup test. H2 is development regression evidence, not Oracle qualification.

The 100k scale fixture now retains its first committed lease and measures three claims for each of all-due, 10%-due/90%-future, 10%-expired-lease/90%-future, mixed 5%-due/5%-expired/90%-future and all-future populations. Remaining samples roll back to bound fixture changes. Population updates are outside the claim measurement. The production query is unchanged. Runtime-user session statistics retain `db block gets`, `consistent gets`, `physical reads`, executions, parses, redo and commits separately, plus non-idle wait counts/microseconds and SQL cursor identities/counters. `DBMS_XPLAN.DISPLAY_CURSOR` must contain actual `A-Rows`, not only estimates.

The million-row workload keeps its four workers, pool eight, owner timing/failure policy, 230-call target and 150-completion floor. It adds actual quota grant/denial counts and separates seed, warmup, measurement and cleanup counters/waits in `oracle-workload-phases.json`. These phases include the explicitly reported observation-query overhead. `statistics_level=ALL` enables actual row-source evidence; it adds instrumentation overhead and is recorded. Timings are diagnostic comparisons, not a claim of an optimization or an SLO. Container limits remain 6 GiB / 4 CPUs. Test-only observation grants are added only to the generated disposable runtime schema; approved external test schemas need equivalent read grants on `v_$session_event`, `v_$sql`, `v_$sql_plan` and `v_$sql_plan_statistics_all` in addition to the existing statistics views. No production grants or indexes are changed.

The full evidence gate requires these plans, distributions, separate counters, consistent claim/quota/owner counts, verified fixture cleanup and all seven Oracle mixed-transaction cases. Raw diagnostics are retained even when a suite or overall run fails. The `4f44080` baseline archive is immutable; each later attempt has its own identity.

The full Failsafe configuration explicitly uses `${project.build.outputDirectory}`. The sample is still repackaged as a Boot executable JAR, whose application classes live under `BOOT-INF/classes`; it cannot serve as Failsafe's ordinary application classpath. The cheap effective-POM audit resolves the sample's inherited full configuration and verifies the compiled-classes directory. Negative guards reject missing or JAR-based configuration. See [Boot's integration-test guidance](https://docs.spring.io/spring-boot/maven-plugin/integration-tests.html). Evidence rejection is recorded separately from execution: stale PIT/Gatling leftovers mean NOT_RUN when no current execution occurred, or FAILED when a current goal executed without valid evidence. Rejected old reports never supply measurements to the new run.

### Queue selector optimization (review in progress)

The selector separates due WAITING and expired LEASED work. Within each weighted priority bucket it alternates waiting/recovery first, falling back when the first lane has no unlocked work. Waiting orders by next-at; recovery orders by earliest lease expiry. Equal deadlines have no promised order. Neither lane can starve the other through repeated expiry. The first ordered locking-range experiment still sorted all due base rows and was rejected. A covering-cursor prototype still sorted all eligible index entries because the client used German NLS sorting for its hash-ID tie-breaker. Query-shape probes rejected COLLATE/NLSSORT expressions that retained the sort. The selected simpler locking range query orders by its numeric deadline only, avoiding locale-dependent hash ordering; it changes no NLS settings. A dedicated 1000-row synthetic probe observed one row/six buffers, with no SORT. Q3 full qualification repeated the required populations; the actual results and remaining future-only cost are in [QM](../../QM.md). Actual Oracle row-source statistics must prove cost; JDBC fetch/max-row settings are not a database-work bound. No Oracle pagination clause is combined with locking.

Fresh schemas include `MS_WORK_RECOVER_IX(priority,work_status,lease_until_ms,work_id)`. Existing v1/v2 schemas need reviewed additive [queue migration 1](../../model-sync-persistence-jdbc/src/main/resources/model-sync/migration/oracle-queue-v1.sql), applied with workers stopped. It preserves the data schema version and refuses to overwrite an existing index. Oracle migration qualification applies the migration to a fixture emulating an older schema and checks data plus index columns.

Eleven default H2 checks are repeated by full-only `OracleSelectionIT`: empty/future populations, ordered due work, locked earliest waiting/recovery candidates and a 20-row locked prefix, concurrent unique claims, repeated recovery competing with arrivals, active leases and priority isolation. The existing atomicity cases remain unchanged. The explicit diagnostic launcher now includes these eleven Oracle cases alongside the seven atomicity cases and complete 100k distribution diagnostic. These are diagnostics, never full qualification.

### Provider scheduling and controlled comparison

The JDBC quota's next-admission query is a consistent, non-consuming hint, outside ledger transactions. Unknown hints preserve custom-port compatibility. Known future hints defer only WAITING bindings. A bounded, instance-local slot per configured provider covers claim/prepare, is rechecked after acquisition, expires with the lease and is released by matching token. It spends no permit and cannot authorize I/O. Authoritative distributed acquire remains after the local planning transaction, immediately before the owner path. Slots never cover owner latency or block another provider; obsolete failures cannot release replacement ownership. Expired LEASED recovery always remains eligible. Fresh reads/visits do not inspect quota.

Eligible bindings rotate within a weighted priority bucket. Fresh schemas include `MS_WORK_BIND_READY_IX(binding_id,priority,work_status,next_at_ms,work_id)`. Existing schemas need additive [queue migration 2](../../model-sync-persistence-jdbc/src/main/resources/model-sync/migration/oracle-queue-v2.sql) after queue migration 1. Startup verifies all required index names through read-only JDBC metadata, without automatic DDL. Apply/review migrations before enabling workers. Tied deadlines have unspecified order; numeric deadline order avoids locale-dependent sorting of hash IDs. Existing row identity, fencing, generations, completion and version checks remain unchanged.

The new default tests cover stale/racing hints, slot expiry and release, empty admission, independent providers, priority weights, fresh reads and compatibility with custom stores ignoring hints. PIT retains the original seven behavioral classes and adds `ProviderClaimScheduler`; `AdmissionHintsTest` joins the existing two contract test classes. The gate stays 85% killed/all generated. DTOs are not added merely to inflate a score.

Full-only `OracleOptimizationIT` seeds 100,000 all-due rows once, then compares baseline OR selection, selector-only, admission-only and combined paths in forward and reverse order. All variants share the current physical table/index catalog, four workers/pool eight, 20 ms spacing, 5/25 ms owner latency, synthetic 429 every 17th entry/timeout every 29th, a 230-owner-entry target and 150-completion floor. Each has ten owner-entry warmups, touched-state reset and fresh no-histogram statistics outside measurement. Global caches are not flushed; table history, statistics, phase costs and plans are recorded. This is a controlled comparison on the new index catalog, separate from historical 1a9187c and million-row qualification.

Before seeding, twenty physical-empty-queue polls per variant must consume zero permits and make zero owner calls. Reports distinguish no-lease polls, store selection attempts, successful selector SQL executions, quota grants/denials, controlled owner entries and persisted completed reconciliations. Native logical gets are consistent + db-block gets; physical reads, sorts/rows, SQL counters, waits, claimed/empty latency and reconciliation lag remain separate. The test-only JDBC callback counter is regression-checked against metadata/state reads; its executed-query count does not imply bounded database work. Actual plans and counters must establish that.

Comparative assertions are declared before execution: all variants retain target/floor and outcome accounting; combined logical gets per completed reconciliation must fall below baseline; combined denials cannot exceed baseline and must fall below selector-only denials, preserving zero when that comparator is already zero. These are comparative regression checks, not production SLOs. Full evidence also rejects SORT in selected production plans and requires real ordered index range scans plus all four variants/two rounds. Missing, malformed or failed comparison evidence cannot qualify. Failed suites retain fresh partial measurements.

Use `python scripts/full-oracle-container.py --queue-diagnostic` only after the current successful default build. It uses the same approved container/settings/preflight and explicitly selects eleven queue correctness cases plus the controlled comparison. Its result remains DIAGNOSTIC_ONLY / full NOT_RUN. Neither diagnostic mode changes the ordinary `-Pfull clean verify` contract. Batching is deferred until measured individual selection cost justifies it; this pass adds no bulk-owner protocol.

The final scale diagnostic also executes three rollback-only frozen-legacy probes in each of the eight distributions, on the same session, physical data and index catalog immediately before the three selected probes. Statistics gathering remains outside both measurements. The first selected all-due lease alone commits; the remaining probes roll back. Both plan families and individual native counters/query counts are required. This controls population/statistics history for selector comparisons without an extra seed or a provider-call workload. Legacy-first ordering may warm caches; it is recorded, and the larger four-way workload reverses variant order.

### Measured Q3 scope and counter limits

Code `ede199a33ad3a0ec5c911110bb84fd7407af1c25`, tree `7c9577e9de49a2f96b1093a65673250a46163da6`, full run `2b75d9cc-8836-4e99-b227-9d836e7e207a`: local configured full PASS (105 Surefire / 26 Oracle tests, PIT 160/162, six Gatling scenarios). Default/PR CI passed 103 JUnit tests. This is separate from production readiness and the unregistered full GitHub workflow. See [QM](../../QM.md) for commands, exits, source/run identity, raw archive and remaining gates.

Report completed selector callbacks separately from ledger claims, quota admission, owner entries and persisted completions. Runtime-user session deltas retain native logical gets, physical reads, executions, parses, redo, sorted rows and waits. Loaded-cursor identities/positive counters discover actual plans, but reloads can retain cumulative counts: one identity-based sum is not a valid window execution total. Do not sum raw `window_executions` or cumulative child values as current calls when continuity is unproven. Callback counts are the reported selector totals; source collector ambiguity remains a follow-up. Raw before/after values and a reviewed derivative are retained.

Improvement is not uniform or a constant work bound: the gathered-stat all-future case used 1859 versus legacy 1710 logical gets and returned no work. Empty/index-history polling economics and arbitrarily large locked prefixes remain unqualified. No claim batch or bulk-owner protocol was added or benchmarked. DDL owned-fixture teardown is measured separately and changes segment history; it is not a row-DELETE or production cleanup benchmark.

### Continuous worker and cursor continuity qualification

The preceding Q3 statements and raw archive are historical and unchanged. The new collector
requires the same SQL/child/address/load time/load count and monotonic observed counters before
calculating a cursor delta. Reloads (including retained cumulative counts), resets, new children
without a before snapshot and disappeared children are `UNATTRIBUTABLE`, with null deltas and
both original snapshots. An unchanged continuous cursor legitimately has a zero delta. Plan
discovery is separate: LAST plans from ambiguous loaded children remain available, explicitly
without attribution to the measurement window. They are not summed as window executions/work.
Selector callbacks and stable runtime-session deltas remain separate observations.

`OracleWorkerIT` is full-only and starts the actual starter auto-configured SmartLifecycle worker,
coordinator, JDBC ledger/quota and JPA projection under the configured JpaTransactionManager.
Its instrumented ports delegate to the real JDBC implementations. It compares the former fixed
250 ms pacing with the auto-configured productive 5 ms / idle 250, 500, 1000 ms policy. Each host
uses four lanes/persistence threads, eight owner threads, queue capacity 64/thread, stable pool
eight, 20 ms main-provider spacing and 5 ms synthetic owner latency. Ten actual-worker warmup
completions and 160 measured backlog completions per variant are separate from seeding.
These are small application-level measurements, not the million-row coordinator benchmark.
The original coordinator benchmark retains its direct `runOne` loops and 5 ms sleep.

Predeclared local diagnostic budgets: after 3 s idle warmup, observe 4 s of empty/all-future
polling. Bounded pacing allows 6 ticks/s plus four boundary ticks (expected steady rate about
4/s for four lanes); fixed control allows 18/s plus four (expected 16/s). Two bindings and
four priorities can require up to twelve completed selector callbacks in an empty tick.
The unchanged `9*ticks+36` assertion is an aggregate measurement-window resource allowance,
not a mathematical per-tick bound. The earlier three-priority rationale was incorrect; its
correction changes no measured numbers, runtime policy or predeclared gate. Adaptive idle native logical gets must stay below 100,000/s in this small
1000-future-row probe. This deliberately generous diagnostic ceiling allows the previously
observed index-history costs with headroom; it is not a production capacity/SLO guarantee or
a proof of constant selector work. Report measured native gets/reads separately. No relative
speedup assertion requires each isolated probe to beat its control.

Urgent local/independent-process enqueue, independent-owner work during main-provider cooldown,
expired-lease recovery, a future WAITING deadline and cooldown expiry have a 2500 ms local
wake/completion budget: the 1000 ms recheck cap
plus 1500 ms diagnostic scheduler/database margin. The independent JVM commits through the
public WorkStore port and reports a commit-time bracket; report lower/upper completion latency,
not HTTP acceptance. Cooldown recovery must relinquish the expired lease without owner I/O,
while the independent owner completes. Main-provider cooldown remains authoritative. Seed,
warmup, pool startup, process startup, table history and observed phases are disclosed.

`python scripts/full-oracle-container.py --worker-diagnostic` selects only this application-level
probe after a current green default build. It is DIAGNOSTIC_ONLY / full NOT_RUN, uses the same
pinned image/6 GiB/4 CPU cap and owned cleanup as full, and does not replace any existing suite.
The full gate now also requires its nonempty Failsafe XML, ten fresh measured phases and their
outcome/resource/wakeup assertions. Failed-run QM retains partial worker measurements.

Native worker deltas also require identical eight SID/serial pairs at the interval endpoints.
Take the after snapshot before cancellation, then stop during a bounded natural idle gap.
Preserve both endpoints and mark a replacement/lost-session interval UNATTRIBUTABLE without
computing a delta. This fixture boundary avoids interrupting an Oracle read and silently counting
replacement-session counters. It is not a production graceful-drain or hung-call qualification.
RetryLater from authoritative admission is classified as deferred; prepare-only/fresh no-fetch
paths share that scheduling counter. It is not a count of quota denials (the quota port records
those separately). Benign pacing phases must report zero infrastructure/owner failures.

Current Q4 full result: source `fad6874`, tree `a530a260738510da84ed7f68fd26a8a75b803d67`, run
`1afffb7d-52ca-4d4a-8857-23c3cefb8f4c`:113 Surefire/27 Oracle, PIT160/162, six Gatling scenarios,
all ten worker phases PASS. [QM](../../QM.md) reports actual config/resources and scope. The
other-provider33ms raw field uses application reconciled-at before COMMIT, not exact committed
observation latency; a fresh committed read verifies the outcome. Other wake fields use observer
timestamps/commit brackets. Polling helpers do not replace production DB statement timeouts.
