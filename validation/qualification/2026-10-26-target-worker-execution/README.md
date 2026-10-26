# Target/worker Oracle diagnostic - 2026-10-26

**Default PASS. Focused diagnostic PASS (5/5). Full NOT_RUN. Production NO-GO.**

Tested clean source **`86488c43b5cfc3616180a50925695d5876bd9e90`**, tree **`98f98b018076cd7ab34dc252b150d7d9bc746832`**,
`Tobia5/model-sync-starter`, branch `review/layered-refactor-qm-20261020`.
The later report-only commit is not claimed as tested source. No implementation,
policy, assertion, resource cap or qualification applicability changed in this task.
Sources baa8d26/86488c4, historical full failure b7f3cb5 and all 19 prior evidence
archives remain unchanged. Earlier QM sections retain their historical scope.

## Commands, source-bound runs and original exits

| Execution | Actual result | Original exits |
|---|---|---|
| `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify` | PASS; default run `e2031493-cbad-4532-a910-e31452cf549a`; 222 JUnit / 102 Python / 41 XML; zero failures/errors/skips; 269.015s build supervision | build 0, qualification 0, execution audit 0, collection 0 |
| Git Bash `scripts/test-triggers.sh` | 86 core / 43 trigger standalone assertions PASS, separately counted from JUnit | 0 |
| Archived `python capacity.py diagnostic`, after build/collection/contracts | PASS; `55e58ae8-6d27-4ba3-b06f-c1806e7b468a`; host 16.579/16.640/16.629 GiB >= unchanged 12 GiB; Docker 19.493 GiB available; C 80.777 / D 1069.780 GiB free | 0 |
| `python scripts/full-oracle-container.py --target-worker-diagnostic` | PASS; run `c8b6153a-94b6-4617-ba45-3b6015200de4`; 5 found/started/passed, zero failures/skips/container failures; 336.232s wrapper supervision / 314.55s infrastructure | preflight Maven 0, JUnit launcher 0, wrapper 0, diagnostic collection 0 |

Infrastructure attempt `ee11204a-e707-4b61-99ce-5dd6fc8c50df` used invocation-only
`MODELSYNC_ORACLE_CONTAINER=EXPLICIT_DISPOSABLE`,
`MODELSYNC_FULL_ENV_ACK=DISPOSABLE_NONPRODUCTION`, and
`MODELSYNC_MAVEN_SETTINGS=.mvn/qualification-central.settings.xml`.
Settings SHA256: `ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`.
Nested Maven retained the same settings and `-B -ntp -Pfull -N initialize`
(preflight), then only the two selected Java classes. This is not full verification.
Preflight reran cheap guards; these are not additional Oracle or JUnit counts.
No million-row seed, 100k comparison, PIT, Gatling, cancellation matrix or full run.
The separate full admission remains 18 GiB and was not invoked.

## Target fixture: creation then existing-row CAS

OracleQualificationTargetIT passed in 4951 ms. Fresh observer connections read:

| Phase | Projection | Validator | Local revision | Generation | Outcome / work |
|---|---|---|---:|---:|---|
| created-A | A | A | 0 | 1 | APPLIED / IDLE |
| changed-existing-B | changed-B | B | 1 | 2 | APPLIED / IDLE |

Digests changed; both projection/state pairs committed. The second owner request
received validator A; exactly two synthetic owner calls occurred. Five SQL statements
were captured only in the changed-existing-row phase, with zero overflow.
Actual Hibernate SQL included:

```sql
select ce1_0.storage_key from sample_customer ce1_0
where ce1_0.storage_key=? and ce1_0.local_version=?
for update of ce1_0.storage_key nowait
```

The capture retains detached lookup/refresh SELECTs and the versioned UPDATE.
The semantic matcher accepted the real `FOR UPDATE OF ... NOWAIT` clause.
Five fresh receipts (one target/four worker hosts in one JVM) assert consumed
point/quota/JPA ORACLE_NOWAIT, ORACLE_26_NOWAIT inserts, DURABLE cooldown,
qualified mapping, ledger-first protocol and actual datasource/JpaTransactionManager
enlistment. Oracle26ai 23.26.3 / JDBC 23.26.3.0.0 / Hibernate 7.4.5.Final were observed.
These two receipt roles do not replace the other full-required roles. Historical
fixture failure remains FAILED; the repaired current test is PASS. No production
locking change was made.

## Four independent actual-worker cells

Actual auto-configured ReactorSyncWorker / SmartLifecycle: four lanes, pool8,
persistence4 / owner8 threads, queue64 per thread, poll250ms, synthetic owner20ms
spacing / 5ms latency. Fixed uses productive250ms / max-idle250ms; adaptive uses
productive5ms / max-idle1s. Each cell has 10 warmup plus 160 measured completions;
the original 20-second assertion was retained. Each executed once.

| Cell | Result | Measured ms | Completed / calls / grants | Ticks / empty | Selector callbacks | Consistent / block gets | Physical reads |
|---|---|---:|---|---|---:|---|---:|
| fresh-fixed | PASS | 15005 | 160 / 160 / 160 | 202 / 42 | 930 | 190235 / 10816 | 0 |
| fresh-adaptive | PASS | 9875 | 160 / 160 / 160 | 212 / 52 | 991 | 130182 / 11337 | 0 |
| statistics-fixed | PASS | 17288 | 160 / 160 / 160 | 242 / 82 | 1258 | 312870 / 11294 | 0 |
| statistics-adaptive | PASS | 9821 | 160 / 160 / 160 | 204 / 44 | 917 | 183233 / 11038 | 0 |

All four eight-session intervals have VALID physical-session continuity. Native
counters include observer SQL, exclude seed/warmup/cleanup, and remain distinct
from selector callbacks and trace events. Sort-row deltas were zero. Worker totals:
640 measured reconciliations/calls/grants; 680 including warmup. The target's two
reads are separate. Nothing represents a million synchronizations.

| Cell | Per-lane finish returns (0,1,2,3) | Empty: occupied / future hint / main eligible | Reservation upper p50 / p95 / p99 / max ms |
|---|---|---|---|
| fresh-fixed | 40 / 40 / 39 / 41 | 24 / 17 / 1 | 22.963 / 25.297 / 35.647 / 36.364 |
| fresh-adaptive | 30 / 30 / 58 / 42 | 28 / 24 / 0 | 21.683 / 25.196 / 27.691 / 33.548 |
| statistics-fixed | 37 / 38 / 38 / 47 | 48 / 33 / 1 | 21.091 / 23.775 / 25.091 / 25.268 |
| statistics-adaptive | 0 / 55 / 37 / 68 | 27 / 17 / 0 | 20.902 / 23.537 / 24.912 / 24.964 |

Per-lane successful finish returns reconcile to 160 independently committed
outcomes per cell. No traced failures, missing lane IDs, dropped events or
unattributed claims. Every empty claim has a scheduling reason. The two main-eligible
selector misses do not alone prove globally absent work at their exact timestamps;
other records may still be leased. Final snapshots: zero eligible work, 170 IDLE
rows, zero pending cooldowns and pool-active0. No 429/cooldown was generated in this
benign fixture; future hints reflect normal admission timing, not lost publication.

Reservation lifetimes are paired observed bounds from schedule-token acquisition,
through lease-token replacement, to release. All 170 per-cell productive tokens
(including warmup) close; none remain unresolved. The upper bounds include observer
call duration. Maxima are 36.364/33.548/25.268/24.964ms, not lease-lifetime retention.
Statistics/adaptive lane0 completed no measured record but made 11 empty attempts
(8 occupied, 3 future hint) with bounded rechecks. It was active, not a terminated
lane. Other lanes completed160; this is not a per-lane fairness SLO.

Per-lane claim/prepare percentiles, tick gaps, wakeup times, hint clocks, token
bounds and raw timestamps are retained in `worker-analysis.json` and the original
`oracle-worker-diagnostic.json`. Wakeup scheduled-to-invoked intervals include
intentional timer delays; they are **not pure scheduler lateness**. Fixed wakeup
p95 is263.318-264.786ms across cells/lanes; adaptive maxima reach1015.087ms with1s
idle backoff. Post-productive residual includes Reactor, observer and dispatch costs,
not an isolated OS metric. No overhead subtraction or clock-only cause inference.

## Measured decision and limits

- **Fixture defect corrected and Oracle-verified:** A/B committed state and actual
  existing-row SQL now prove the intended path with unchanged adapters.
- **No worker production defect demonstrated in these cells:** both modes passed,
  all lanes continued attempts and reservations released. Initial tick spreads
  were0.202/0.066/0.055/0.025ms; synchronized starts did not create a persistent
  one-success-per250ms pattern here. Historical full timeout remains FAILED and
  its exact cause unresolved. This does not requalify the earlier failed run.
- **Virtual counterexamples retain their model scope:** fixed80 versus adaptive160
  at20s used deterministic immediate local execution. Oracle has observed method,
  dispatch and I/O costs and uneven lane progress. Similar or different throughput
  does not establish the historical cause. Tracing/Mockito overhead was not
  calibrated with an untraced paired control; observer-induced phase changes
  cannot be excluded.
- **Statistics only:** fresh cells had no optimizer estimates; history cells set
  MS_WORK NUM_ROWS100000/BLOCKS2260 on small data. Physical segment/high-water,
  index/cache state and prior full workload were not reproduced. Sequential order
  and raw snapshots remain; no global flush or million-row seed occurred.
- **No criterion change justified/adopted here:** both fixed cells meet the existing
  threshold. The earlier proposal remains unapproved and unimplemented. Keep
  160/20s, all83 full-required methods/18 suites, policies and resource limits.

## Cleanup and remaining release gates

Every cell reports DRAINED (0ms final close receipt), logical/physical/owner0,
pool-active0 and pool closed. Independent checks confirm absence of this run's
container `msq-8e9831284ef2`, volume `msq-8e9831284ef2-data` and JVM PID19596.
Docker inspect exits1 were expected explicit "no such object/volume", not ignored
errors. No unrelated workload, container or global setting was changed.

JDK21.0.12.1 / Maven3.9.9 / Python3.12.10 / Docker29.8.2. Pinned image:
`ghcr.io/gvenzl/oracle-free:23.26.3-slim@sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`.
Oracle6GiB/4CPU, launcher512MiB heap/128MiB direct, generated per-run credentials,
separate runtime/migration schemas and loopback-only publication were retained.

Current **full NOT_RUN**, by scope, not a five-method full PASS. All83 Oracle
methods/18 suites, mutation/load, atomicity/recovery/shutdown/transport, provider
and migration gates remain required for integrated qualification. Historical
independent cancellation FAILED; current characterization NOT_RUN. Unrecorded
responses, uncertain outcomes, receipt retention, security, operations and other
parent gates remain open. Production NO-GO. No merge, main change, workflow
bootstrap or package publication.

[Root QM](../../../QM.md).
The root QM preserves all historical content unchanged.

---

Archive SHA256 `120af8d9b7ad450c775db36e2ae6a0809e70b1b7f027fe51a00057263cd47769`; 653,385 bytes / 141 entries.

[Integrity receipt](report-verification.json): CRC and entry digests passed;
all 19 prior archives and the historical QM suffix remain unchanged. Post-report
docs, architecture and diff checks passed.

## Per-lane measured trace detail

Counts exclude warmup. Durations include observer overhead. Empty reasons are actual
scheduling decisions, not arithmetic remainders. End time is reconstructed from
rounded elapsed_ms (<=1ms uncertainty); all phase/event counts reconcile exactly.

| Cell / lane | Claims / returned / finishes | Empty occupied / future / eligible | Claim p50 / p95 / p99 ms | Prepare p50 / p95 / p99 ms | Tick-gap p50 / p95 ms |
|---|---|---|---|---|---|
| fresh-fixed / 0 | 51 / 40 / 40 | 11 / 0 / 0 | 11.842 / 14.023 / 15.147 | 11.251 / 12.166 / 12.662 | 308.642 / 325.476 |
| fresh-fixed / 1 | 50 / 40 / 40 | 0 / 10 / 0 | 12.311 / 14.805 / 16.474 | 11.504 / 13.48 / 15.433 | 308.864 / 324.935 |
| fresh-fixed / 2 | 51 / 39 / 39 | 9 / 2 / 1 | 12.285 / 14.976 / 16.57 | 11.362 / 12.558 / 12.784 | 309.066 / 325.399 |
| fresh-fixed / 3 | 50 / 41 / 41 | 4 / 5 / 0 | 12.222 / 14.395 / 24.546 | 11.295 / 12.903 / 26.372 | 308.762 / 324.876 |
| fresh-adaptive / 0 | 42 / 30 / 30 | 9 / 3 / 0 | 11.076 / 13.759 / 15.189 | 11.198 / 11.992 / 18.534 | 62.047 / 1022.153 |
| fresh-adaptive / 1 | 44 / 30 / 30 | 6 / 8 / 0 | 10.959 / 13.72 / 14.414 | 11.112 / 12.229 / 12.997 | 62.06 / 1021.225 |
| fresh-adaptive / 2 | 70 / 58 / 58 | 8 / 4 / 0 | 11.6 / 13.693 / 15.412 | 10.48 / 12.515 / 14.588 | 62.014 / 526.761 |
| fresh-adaptive / 3 | 56 / 42 / 42 | 5 / 9 / 0 | 11.297 / 13.924 / 16.958 | 10.628 / 12.205 / 13.78 | 62.009 / 526.312 |
| statistics-fixed / 0 | 61 / 37 / 37 | 17 / 7 / 0 | 10.7 / 12.883 / 13.268 | 10.32 / 11.62 / 11.89 | 293.582 / 309.204 |
| statistics-fixed / 1 | 60 / 38 / 38 | 14 / 8 / 0 | 10.442 / 13.005 / 13.551 | 10.262 / 12.769 / 13.044 | 293.694 / 309.497 |
| statistics-fixed / 2 | 61 / 38 / 38 | 8 / 15 / 0 | 11.109 / 13.873 / 14.619 | 10.219 / 12.274 / 12.335 | 293.715 / 309.272 |
| statistics-fixed / 3 | 60 / 47 / 47 | 9 / 3 / 1 | 11.492 / 13.58 / 15.059 | 10.045 / 11.92 / 12.884 | 293.859 / 309.866 |
| statistics-adaptive / 0 | 11 / 0 / 0 | 8 / 3 / 0 | 9.556 / 10.693 / 10.693 | NO_SAMPLE / NO_SAMPLE / NO_SAMPLE | 1019.787 / 1022.944 |
| statistics-adaptive / 1 | 65 / 55 / 55 | 6 / 4 / 0 | 10.75 / 13.167 / 14.908 | 10.434 / 11.511 / 11.662 | 61.896 / 1020.771 |
| statistics-adaptive / 2 | 49 / 37 / 37 | 6 / 6 / 0 | 10.759 / 13.054 / 24.227 | 10.483 / 11.242 / 11.619 | 62.027 / 1020.134 |
| statistics-adaptive / 3 | 79 / 68 / 68 | 7 / 4 / 0 | 11.139 / 13.457 / 14.178 | 10.594 / 11.505 / 11.872 | 61.943 / 524.887 |

Zero prepare samples for statistics/adaptive lane0 are NO_SAMPLE, not zero latency.
It made11 attempts with approximately1s bounded rechecks. No per-lane fairness SLO.
`worker-analysis.json` retains p99/max and all separate clock/counter summaries.

## Artifact inventory and boundaries

- `frozen-source*.json`: clean exact source, run identities and all19 earlier archive hashes. No source edits or concurrent build during execution.
- `default-*/`: original command/log/XML, marker, run-scoped QM and all validation/audit/collection exits for e2031493-cbad-4532-a910-e31452cf549a.
- `diagnostic-*/`: original wrapper log, exits, run context, infrastructure, five-method launcher results, A/B projection SQL, four-cell raw trace and five effective host receipts for c8b6153a-94b6-4617-ba45-3b6015200de4.
- `worker-analysis.json`, `analyze-worker.py`, `analysis-command.log`: read-only reproducible nearest-rank distributions, token pairing and per-lane attribution assertions. Raw snapshots/events unchanged.
- `diagnostic-integrity.json`, `diagnostic-evidence.json`, `run-scoped-QM.md`: source/run binding, partial scope, unchanged gates and historical/current status. No full validator converts this selection into full PASS.
- `scoped-capacity-*.json`, `capacity-command.*`, `owned-infrastructure-observation.json`, `cleanup-and-preservation.json`: one admission, actual nonsecret caps/image/mount/loopback and independent container/volume/PID absence.
- `session.py`, `capacity.py`, `diagnostic-reader.py`: existing external supervisor/checks. `collector-normalization.json` records removal of only a six-byte mojibake BOM from the copied prepared reader, preserving logic/assertions and original/new hashes.
- `standalone-contracts.*`:86/43 assertions, exit0, separate from JUnit.
- `report-preparation-failure.json`: a report-generation quoting error exited1 before file creation or repository changes; no build/test/Oracle rerun or altered result. Fixed report preparation is distinct from successful execution.
- `nonsecret-artifact-check.json`, `raw-archive-manifest.json`, `SHA256SUMS`: bounded credential-format scan and all raw entry/archive seals, not general security certification.

The default context was archived before the diagnostic invalidated it and began a
new run. Default counts belong to the actual earlier current-source default run,
not the diagnostic marker. No historical suites supply missing current methods.
Wrapper scope selects exactly OracleQualificationTargetIT/OracleWorkerDiagnostic.
The generic infrastructure qualification_target=null in diagnostic mode; the Java
launch property and five effective receipts identify the named target. This does
not indicate DEFAULT fallback or completed full-role/inventory qualification.

No build/test failure occurred in this execution. Historical FAILED runs and the
previous resource BLOCKED attempt retain their sealed source/run status. No
unchanged diagnostic retry occurred. No new criterion is proposed by this pass.
