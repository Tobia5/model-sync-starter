# Integrated DURABLE named-target qualification - 2026-10-25

**Default PASS. Configured full FAILED. Production NO-GO.**

Tested clean source **`7c1abe5dce3cb9ee83ae0110bb86de234dc7556d`**, tree **`c604e462bd63b1a2a2bcdcdfd1b66aa8f3af7663`**, branch
`review/layered-refactor-qm-20261020` in `Tobia5/model-sync-starter`.
The later report-only commit is not claimed as tested code. No production code,
locking, scheduling, applicability, workload, threshold or resource setting changed.
Sources5b96b47/1cfbefb, report7c1abe5 and all17 earlier raw archives remain unchanged;
the seven-method diagnostic stays attributed to5b96b47.

## Commands, identities and exits

Approved Maven settings: `.mvn/qualification-central.settings.xml`, SHA-256
`ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`.

| Execution | Current evidence | Original exits |
|---|---|---|
| `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify` | PASS; run `c789975e-1a6f-4d90-9d0c-ae0c73eee998`;189 JUnit/98 Python/38 XML,0 bad;216.100s supervisor runtime | build0, report0, execution audit0, collection0 |
| Git Bash `scripts/test-triggers.sh` |86 core/43 trigger assertions PASS; separate from JUnit |0 |
| Post-collection `python capacity.py full` (archived helper) | PASS; host21.563/21.648/21.564GiB >=18; Docker19.969GiB available; C83.048/D1069.891GiB free |0 |
| `python scripts/full-oracle-container.py` | FAILED; run `d7fd02b7-1d9d-430c-a8ef-dfa883e8add6`;5976.738s wrapper runtime;191 Surefire PASS,83 Oracle/18 suites:81 PASS,2 FAILED,0 errors/skips | wrapper1, Maven1, qualification1, collection0 |

The full wrapper used invocation-only `MODELSYNC_ORACLE_CONTAINER=EXPLICIT_DISPOSABLE`,
`MODELSYNC_FULL_ENV_ACK=DISPOSABLE_NONPRODUCTION` and the same
`MODELSYNC_MAVEN_SETTINGS`. Nested Maven retained `-B -ntp -s <approved-settings>
-fae -Pfull clean verify`. No diagnostic selector or unchanged retry ran.

## Required failures retained

1. **OracleQualificationTargetIT FAILED**, line42: no captured Hibernate SQL
   contained `for update nowait`. Adapter/platform/mapping/enlistment receipts
   passed; they do not satisfy this separate SQL assertion. The fixture owner
   returns the same replica value twice: first creation then the equal-value
   `MATCH` path, which skips projection CAS. This source inspection explains a
   missing existing-row exercise; the failing fixture did not persist its SQL
   list, so no replacement SQL observation is invented. Separate lock-policy
   cases passed and are not reopened. Follow-up: exercise the actual existing-row
   lookup/refresh in this fixture, retaining the assertion and all policies.
2. **OracleWorkerIT FAILED**, fixed-mode productive phase: the20,000ms condition
   timed out at20,003ms. Four lanes,250ms poll/productive/maximum-idle delays,
   persistence4/owner8 threads, pool8, owner20ms spacing/5ms latency.
   Completions94 total (10 warmup +84 measured),76 WAITING/94 IDLE.
   Interval deltas:295 ticks,211 empty,84 owner calls/grants/completions,
   0 failed ticks,1670 selector callbacks; stable eight-session interval,
   416334 consistent gets,7753 block gets,1 physical read.
   Worker running=true and physical/logical0 at the failure snapshot do not
   prove lane health or successful drain. Cause remains unestablished; no pacing
   redesign, deadline extension or arbitrary error acceptance was made.
   Bounded/adaptive, idle, wakeup and recovery phases in this method are NOT_RUN.

## Passing partial evidence

- All83 mandatory Oracle method names matched the current83/18 inventory.
  Both seven-case atomicity modes, six process-recovery cases, eleven applicable
  I/O cases, seven lock-policy, six quota, twelve first-creation and six new
  cooldown methods executed. All except the two methods above passed.
- Runtime receipts verified actual point/quota/JPA `ORACLE_NOWAIT`,
  `ORACLE_26_NOWAIT`, `DURABLE`, qualified mappings and shared datasource/
  `JpaTransactionManager`. All five required host roles are present. Oracle
  migration consumed extension version1; schema/runtime receipts are retained.
  The SQL-proof failure still prevents target qualification.
- PIT1.25.9/provider1.2.3:167 KILLED/1 SURVIVED/168 generated,**99.40% killed/generated**,
  0 NO_COVERAGE/TIMED_OUT/RUN_ERROR.85% gate unchanged. The surviving
  `FreshnessPolicy.positive(maxAge)` removal remains counted; positive refresh
  plus refresh<=max rejects nonpositive max, and null comparison rejects null.
  Existing equivalence review does not equate exception messages.
- Eight comparison variants PASS:3425 claims/acquisitions =1765 grants +1138
  denials +522 captured LocalContention refusals (SQLState61000/vendor54).
  1765 measured owner calls,1603 committed reconciliations; no unknown/uncertain
  acquisition outcomes, escaped lanes or active operations.104 logical cooldowns,
  104 acceptance commits,119 apply attempts =104 commits +15 refused rollbacks;
  all104 final APPLIED,0 unresolved/logical failures.32/32 lanes finished normally.
- Oracle capacity:1,000,000 seeded (100,000 due/900,000 future),100 hot requests,
  seed4,327,625ms. Four direct coordinator workers/pool8:200 measured completions
  from220 measured owner calls in9584ms (20.868 syncs/s);210 completions/230 calls
  with warmup.13 synthetic429s/7 timeouts.338.191 summed DB work units per measured
  owner call; individual counters retained. Claimed runOne p50/p95/p99=37/59/62ms;
  lag4340740/4345255/4345702ms includes seeding. This is not actual-worker capacity,
  a production SLO or a million completed synchronizations.
- Separate actual cooldown worker PASS:2 owner calls/2 committed admissions,
  unrelated-provider completion, shared pending proof with blocker held, recovery
  without another read; DRAINED11ms, physical/owner/pool0. This does not replace the
  failed pacing method. Six in-memory Gatling scenarios and their HTTP assertions
  PASS:367 requests,0 HTTP errors; not Oracle capacity evidence.

Environment: JDK21.0.12.1, Maven3.9.9, Python3.12.10, Docker29.8.2,
Boot4.1.1, JUnit/launcher6.0.3, Hibernate7.4.5.Final, Oracle26ai23.26.3.0.0/
JDBC23.26.3.0.0, Surefire/Failsafe3.5.3, Gatling3.13.1/plugin4.11.0.
Pinned Oracle image and unchanged6GiB/4CPU caps are in the infrastructure receipt.

## Cleanup, applicability and remaining gates

Container `msq-f84fcf0a6e32` and volume `msq-f84fcf0a6e32-data` removed; wrapper
checks and independent exact-name inspections agree. All12 PIDs from current-run
host receipts are absent. No residual owned infrastructure was observed.
Historical independent cancellation remains FAILED (`2c39b75`/`43040e7`);
current characterization NOT_RUN. DEFAULT, older Oracle and arbitrary replacement
beans have no named bounded-persistence qualification.

The two current failures prevent configured full acceptance. Unrecorded provider
response/failed acceptance windows, uncertain outcomes, receipt retention and
maintenance, compliant rollout, security, operations and other parent release
gates remain open. Even a future configured full PASS is not production acceptance.

[Root QM](../../../QM.md)
includes inventory, comparison/Gatling tables and raw reports. Archive SHA-256:
`f430ab0dc388fe1f413862366d492dbe0feed7c30c838c808332fd1e096fccf5`. Original run-scoped `target/qm/QM.md`, logs, partial checks and raw
reports are inside the archive. No merge, main change, workflow bootstrap or
package publication occurred. The root QM retains earlier history byte-for-byte.

---

## Required Oracle suite inventory

The archived `qualification-target.json` names all83 methods. Fresh XML method names
were compared exactly, including both failed methods; none was substituted or skipped.

| Suite | Methods | Failures | Seconds |
|---|---:|---:|---:|
| OracleSmokeIT | 1 | 0 | 0.626 |
| OracleConcurrencyIT | 3 | 0 | 1.183 |
| OracleMigrationIT | 1 | 0 | 3.992 |
| OracleScaleIT | 1 | 0 | 568.172 |
| OracleCapacityIT | 1 | 0 | 4360.915 |
| OracleSelectionIT | 11 | 0 | 0.721 |
| OracleOptimizationIT | 1 | 0 | 549.007 |
| OracleCooldownIT | 5 | 0 | 2.099 |
| OracleAtomicityIT | 7 | 0 | 9.470 |
| NowaitOracleAtomicityIT | 7 | 0 | 15.113 |
| OracleWorkerIT | 1 | 1 | 23.437 |
| OracleRecoveryIT | 6 | 0 | 70.429 |
| OracleIoIT | 11 | 0 | 18.611 |
| OracleLockPolicyIT | 7 | 0 | 6.754 |
| OracleQuotaContentionIT | 6 | 0 | 6.291 |
| OracleFirstCreationIT | 12 | 0 | 11.004 |
| OracleQualificationTargetIT | 1 | 1 | 0.561 |
| OracleCooldownWorkerIT | 1 | 0 | 1.890 |

## Controlled comparison (current integrated run only)

Same100k all-due fixture, four direct workers/pool8, owner20ms spacing/5-or25ms
latency,429 every17/timeouts every29. Same NOWAIT quota/Oracle26 insert/DURABLE
policy across variants. Forward/reverse order, original completion floors and
phase/cursor/session assertions retained. Statistics/cache/table history and
setup/cleanup work are in raw reports; timing differences from older runs are
not isolated causal measurements. No logical-get share is called wall-time share.

| Round / variant | ms | Claims | Grants / denials / refusals | Owner calls | Syncs | Cooldowns / logical failures | Native logical gets/sync |
|---|---:|---:|---|---:|---:|---|---:|
| 0 / baseline | 43862 | 329 | 222 / 76 / 31 | 222 | 201 | 13 / 0 | 166377.119 |
| 0 / selector-only | 7813 | 941 | 220 / 489 / 232 | 220 | 200 | 13 / 0 | 574.910 |
| 0 / admission-only | 76308 | 220 | 220 / 0 / 0 | 220 | 200 | 13 / 0 | 113443.565 |
| 0 / combined | 9103 | 223 | 220 / 1 / 2 | 220 | 200 | 13 / 0 | 344.255 |
| 1 / combined | 9558 | 224 | 220 / 3 / 1 | 220 | 200 | 13 / 0 | 347.960 |
| 1 / admission-only | 74555 | 220 | 220 / 0 / 0 | 220 | 200 | 13 / 0 | 113400.700 |
| 1 / selector-only | 7416 | 946 | 220 / 499 / 227 | 220 | 200 | 13 / 0 | 578.725 |
| 1 / baseline | 47669 | 322 | 223 / 70 / 29 | 223 | 202 | 13 / 0 | 162080.584 |

Warmup is separate (10 owner reads/completions per variant,80 total). Raw per-attempt
correlation and detached failures establish the522 current refusals; historical55
remain unclassified and historical five publication failures remain FAILED.
No arithmetic remainder has been relabelled as a measured failure.

## Gatling semantics (in-memory test harness)

| Scenario | HTTP requests / errors | HTTP p50/p95/p99 ms | Accepted visits | Owner calls (incl fixture setup) | Completed-row counter | Measurement+drain ms |
|---|---|---|---:|---:|---:|---:|
| cooldown | 3 / 0 | 4/25/25 | 0 | 2 | 0 | 462 |
| event | 4 / 0 | 2/24/24 | 1 | 2 | 0 | 553 |
| fresh | 40 / 0 | 77/179/179 | 40 | 1 | 0 | 750 |
| hot | 100 / 0 | 212/215/216 | 100 | 1 | 0 | 774 |
| leases | 200 / 0 | 273/283/286 | 0 | 95 | 95 | 1079 |
| unsafe | 20 / 0 | 67/71/71 | 20 | 0 | 0 | 596 |

Fresh-cache setup performs one initial owner read; fresh visits add none. The raw
completed-row counter counts only the synthetic lease-ledger keys; its zero in
other scenarios is not a general completed-sync count. It is not synthesized
from accepted visits/HTTP202. Harness
projection/ledger assertions remain the semantic checks. This tiny local load is
not Oracle throughput, production provider capacity or long-duration qualification.
Each Gatling JVM uses1GiB heap; leases has200 synthetic records/200 job requests.
At-once concurrency: fresh40, hot100, unsafe20, event1, cooldown1, leases200;
recorded warmup0. The unchanged HTTP gate is0% failed requests; latencies are
measurements, not invented SLOs. Owner/projection assertions supply the semantic
rationale, and these brief scenarios do not establish sustained capacity.
Other concurrency, startup/drain behavior and selected Java simulations remain
source-linked in `model-sync-performance` and raw logs; thresholds were unchanged.

## Mutation scope and investigation

`io.modelsync.core.domain.FreshnessPolicy`, `io.modelsync.core.application.SyncAccess`, `io.modelsync.core.application.SyncCoordinator`, `io.modelsync.core.application.ProviderClaimScheduler`, `io.modelsync.core.application.SyncPlanner`, `io.modelsync.core.application.SyncResultApplier`, `io.modelsync.core.application.SyncFailurePolicy`, `io.modelsync.core.application.SyncJob`

PIT reported7s engine runtime and452 test executions across mutants; those
executions are not additional current JUnit suite tests.

Four test classes: CoreContractsTest, TriggerContractsTest, AdmissionHintsTest,
ContentionPolicyTest. XML, HTML and executed plugin/configuration logs are retained.
No business exclusions/threshold changes were added for this run. The sole
survivor is still in the denominator. See the root historical equivalence review
and FreshnessPolicy constructor at this exact source.

## Evidence layout and collection

- `default-fa3c9458-ab18-48fc-ab37-794fb823c341/`: original default build, report/audit/collector outputs,89 raw files and hashes.
- `full-547b1a99-e0da-436b-85df-8c44d4f2e89a/`: original failed build and qualification logs, collector0,408 current raw files and hashes, including run-scoped QM and67MB detailed evidence JSON.
- `baseline.json`, `frozen-source-end.json`, `preservation-during-run.json`: clean-source identity and preserved17 historical archive/root-QM hashes.
- `scoped-capacity-*.json`, `capacity-command.json/log`: post-default bounded admission and original exit0. No unbounded wait/retry or budget change.
- `independent-cleanup.json`, `owned-processes.json`: bounded exact-owned-resource observations and original query exits.
- `measured-summary.json`, `summarize.py`: read-only partial validation. Collection exit0 records component statuses; actual-worker validation remains FAILED/1 and overall full FAILED. These are no new test executions or substitute full gates.
- `session.py`, `capacity.py`, `publish-report.py`: exact local orchestration and report derivation. No credentials/settings overrides outside the approved project file.
- `nonsecret-artifact-check.json`: bounded48-hex credential-format scan found no matching file; not a general secret-detection guarantee. No complete container environment was exported.
- `raw-archive-manifest.json`: entry hashes; `SHA256SUMS` seals the zip.

The full collector has default lane NOT_RUN because that collection belongs to
the full run only. The separately archived fresh default is PASS at its own run
identity; no marker/reports were borrowed. Report generation preserved Maven1,
qualification1 and fresh failing measurements. Full gate rejected the two failed
suites; the performance module evidence-gate failure is consequential, not an
additional Gatling scenario failure. No unchanged qualification rerun occurred.
