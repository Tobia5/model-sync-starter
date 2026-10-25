# Cooldown publication/recovery qualification - 2026-10-25

## Sources and outcomes

- Final clean source: `1cfbefb5be942b97bba0825af7ca83063ae39b16`, tree `a80a1da1e645c6d74291f1072dc9771ac0f62e92`.
- Target: `oracle26-nowait-ledger-first-v1`, named configuration only, now requiring explicit `cooldown-policy=DURABLE` and optional schema extension version1 alongside the existing NOWAIT/INSERT/JPA policies.
- Final default: PASS, run `e21b2c6f-4d99-4f9c-b331-32ce89af0956`,189 JUnit tests/98 Python guards,38 XML reports,0 failures/errors/skips; build/audit/qualification/collector exits0. Maven runtime321.574s. Offline core86/trigger43 assertions also passed on final source; these are not extra JUnit tests.
- Focused Oracle: source `5b96b47c1ab24f2afa0b8fe656a6be4c724f07c6`, tree `c12839418f69987414ea27f636b63beb3d29f8ac`, run `943ef32d-9474-40db-8887-e92df5b880b8`:7/7 methods PASS (five publication, one migration, one actual worker); preflight/launcher/evidence exits0,297.481s overall. Production/Java fixtures are identical at final source; the later four-line change only tightened Python unknown-outcome validation/tests. This focused run is not current full qualification.
- Controlled comparison: final-source run `6edac4cd-17e1-44c9-bd34-d7e8657d31b1`, eight of eight variants and independent detailed validation PASS; preflight/launcher/evidence exits0,932.494s overall.
- Integrated full: BLOCKED at resource admission attempt `266ba1b2-6acb-42ae-aa88-f5c57c45c2cd`, Python admission exit2. Host samples16.304/16.272/16.316GiB versus unchanged18GiB minimum. Docker19.974GiB unallocated capacity and C66.878/D1069.897GiB free passed. Full wrapper/Maven/83 mandatory Oracle methods/PIT/Gatling NOT_RUN; no full resources created. The prior default marker was invalidated; blocked collection has attribution NONE and no reused suites. Corrected diagnostic collector exit0; an initial report-orchestration KeyError exit1 and its outputs are retained separately.
- Production: NO-GO. Configured full acceptance, unrecorded-response/failure windows, uncertain outcomes, receipt retention, security and operations remain open.

## Actual commands and configuration

`mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify`

`C:/Program Files/Git/bin/bash.exe scripts/test-triggers.sh`

With invocation-only `MODELSYNC_ORACLE_CONTAINER=EXPLICIT_DISPOSABLE`, `MODELSYNC_FULL_ENV_ACK=DISPOSABLE_NONPRODUCTION`, `MODELSYNC_MAVEN_SETTINGS=.mvn/qualification-central.settings.xml`:

`python scripts/full-oracle-container.py --cooldown-diagnostic`

`python scripts/full-oracle-container.py --comparison-accounting-diagnostic`

The selected diagnostic entry points run `-Pfull -N initialize` and a source-bound JUnit launcher, not full verify. Exact nested commands, environment versions, original exits and commands for every default/development/failed attempt are in each run folder. Intended full command `python scripts/full-oracle-container.py` was NOT_EXECUTED after the failed18GiB admission; no Maven exit is invented for it.

Settings SHA256: `ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`.
Actual JDK21.0.12.1, Maven3.9.9, Boot4.1.1, JUnit/launcher6.0.3, Hibernate7.4.5.Final, Oracle26ai23.26.3.0.0/JDBC23.26.3.0.0, Python3.12.10, Docker29.8.2. Surefire/Failsafe3.5.3 remain pinned. PIT1.25.9/provider1.2.3 and Gatling3.13.1/plugin4.11.0 remain pinned and unexecuted this full attempt;85% mutation gate/scope unchanged.

Oracle image: `ghcr.io/gvenzl/oracle-free:23.26.3-slim@sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`. Same6GiB/4CPU caps, loopback ports, generated credentials and separate runtime/migration schemas. Diagnostic12GiB and full18GiB budgets were retained. No unrelated resource was stopped or reconfigured.

## Publication and recovery findings

- Existing quota held: first materialization refused54/rolled back, competitor resolved normally during the retry budget, second attempt committed the original deadline; client85ms. The first refusal/server observations occurred before competitor release.
- Held beyond budget: four refusals/rollbacks, client123ms, DURABLE_PENDING independently visible while competitor remained held. New admission refused, another provider granted, later fresh admission recovered the accepted barrier. One admission whose guard preceded acceptance was explicitly in flight; pending acceptance does not recall it.
- Duplicate receipt insertion: eight native INSERT NOWAIT refusals across competitor commit/rollback cases, canonical identity/deadline retained, out-of-order updates did not shorten the quota floor. Failed acceptance remained local-only infrastructure protection, not distributed acceptance.
- Owned JVM terminated after committed PENDING acceptance: exact server session disappeared, a fresh gate in the independent parent process denied/recovered from the durable row without an owner read.
- Cooldown committed before held-work metadata refusal: fresh connections saw APPLIED cooldown, LEASED work and no success state. Retrying metadata used the canonical deadline and exactly one original owner read.
- Actual auto-configured Reactor worker/JPA/JDBC host:4 lanes,pool8,productive5ms,poll250ms,max-idle1000ms,shutdown budget5000ms. Two owner reads/two local committed admissions, one independent-provider completion; the provider429 retained its existing FAILED tick classification. Work stored COOLDOWN_PENDING, worker kept running, shutdown DRAINED in8ms with logical0/physical0/owner0 and pool0 before competing transaction release. A fresh gate recovered without another owner read. This is a small application-level semantic/recovery probe, not capacity evidence.

Raw exact SQL, SQLState/vendor facts, before/after rollback server snapshots, transaction counters, physical drain and owned-resource receipts are retained. Quota and receipt point SQL uses FOR UPDATE NOWAIT; acceptance uses native INSERT ... NOWAIT. Emergency release was not credited as refusal/cancellation proof. Historical plain-UPDATE independent cancellation stays FAILED (source2c39b75/report43040e7); current characterization NOT_RUN.

## Original controlled comparison

100,000 due records, four concurrent workers/pool8, same quota NOWAIT and DURABLE publication policies in every variant;50 owner calls/s configured,5/25ms controlled latency,429 every17,timeout every29. Ten owner-entry warmup then target230 total entries per variant, original3-minute measurement bound and5ms runOne-loop sleep. Statistics/history/warmup/native plans and phase counters are in raw JSON; no global cache flush. Seed114581ms and all reset/cleanup costs are separate from productive measurement. No million-row fixture was seeded.

| Round/variant | Claims | Grant / denial / thrown refusal | Owner calls | Committed syncs | Logical pub / immediate APPLIED / failed | Physical apply attempts / refusals | Measurement ms |
|---|---:|---:|---:|---:|---:|---:|---:|
| 0/baseline | 325 | 223 / 64 / 38 | 223 | 202 | 13 / 13 / 0 | 13 / 0 | 50383 |
| 0/selector-only | 758 | 221 / 363 / 174 | 221 | 201 | 13 / 13 / 0 | 15 / 2 | 8262 |
| 0/admission-only | 220 | 220 / 0 / 0 | 220 | 200 | 13 / 13 / 0 | 13 / 0 | 91132 |
| 0/combined | 224 | 220 / 2 / 2 | 220 | 200 | 13 / 13 / 0 | 13 / 0 | 9676 |
| 1/combined | 224 | 220 / 3 / 1 | 220 | 200 | 13 / 13 / 0 | 15 / 2 | 9763 |
| 1/admission-only | 220 | 220 / 0 / 0 | 220 | 200 | 13 / 13 / 0 | 13 / 0 | 91930 |
| 1/selector-only | 906 | 220 / 463 / 223 | 220 | 200 | 13 / 13 / 0 | 19 / 6 | 7536 |
| 1/baseline | 305 | 222 / 52 / 31 | 222 | 201 | 13 / 13 / 0 | 13 / 0 | 52997 |

Totals:3182 committed claimed attempts =3182 acquisitions =1766 grants+947 returned denials+469 directly captured LocalContention failures (SQLState61000/vendor54). Zero unknown/uncertain/unresolved outcomes, failure-metadata errors, escaped polls or duplicate admission use/refunds. Owner calls1766 and committed reconciliations1604 are independently measured;104 provider429 and58 controlled timeouts account for the remaining owner outcomes. Warmup adds80 owner reads/completions separately.

All32 lane runs finished normally, with positive polls per lane. Every physical drain showed zero active owner/pool/server transaction/request counts.104 logical publications/104 immutable accepted receipts/104 final APPLIED outcomes;104 acceptance commits,114 materialization attempts=104 commits+10 confirmed refusal rollbacks;0 immediate pending returns in this load,0 logical failures,0 unresolved after bounded publication drain. The forced small fixture separately proves pending behavior.

Acquisition refusals increased versus the historical36ec8ca run (102 to469); total claims2804 to3182. This is reported as additional observed admission cost, not hidden behind denials or a speedup claim. Cross-variant combined denial+failure and database-work assertions still passed. p50/p95/p99, native logical/physical gets, rows/sorts, execution plans, waits, counters and reset/unattributable cursor intervals remain in raw reports. Logical-get shares are not wall-clock shares; direct runOne throughput is not continuous-worker throughput.

## Preserved failures, cleanup and archive

Initial clean source `c89f9fa7822aa226948cecd6ff3ef9f17d872f65`, tree `47140310be2f6f9cbbe6898cb94004f55e11023d`, Oracle run `0c1caad5-3974-4665-a243-5e0ac3aa3e12`: preflight0/launcher1/evidence1,4/5 passed, metadata case FAILED with NoClassDefFoundError for the testkit fixture; migration/worker NOT_RUN. Its immutable raw results and the focused classpath fix remain separate. Five development attempts preserve original command exits/logs (four failed, one passed); they are unqualified dirty development runs, not source-frozen full evidence.

All three diagnostic containers/volumes were removed and independently confirmed absent. The owned child JVM/server session and fixture executors/pools were checked; raw case receipts show exact outcomes. The blocked full created none. All16 earlier evidence ZIP hashes and original root QM bytes matched before this report. Historical36ec8ca/report26414a9 five publication failures,25297fd/reportd196236 full FAILED and unclassified55 remain unchanged.

[Raw evidence](raw-evidence.zip): SHA256 `bc9319ca5ce0042c2f7321c951d14a97edc85fb0b2a438b81c594a7bdfbda923`. Contains all attempt directories, fresh reports, commands/exits, supervision/resource scripts, before/after counters, classpaths/tool versions, cleanup receipts, preservation manifest and blocked full report. The initial blocked-report collection orchestration error is retained; regeneration collected no suite evidence and did not rerun qualification.

A configured full PASS has not been obtained for final source. Complete all83 Oracle methods and retained atomicity/recovery/shutdown/transport/quota/architecture/mutation/load gates on the exact clean source after18GiB admission. No merge, main change, workflow bootstrap or package publication occurred.
