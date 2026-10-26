# Integrated repaired named-target qualification - 2026-10-26

**Default PASS. Configured full PASS. Production NO-GO.**

Tested clean source **`a0f46d7a6f188263f8822e3ad728e1894076871a`**, tree **`0382fbeb887e3b3c2a55533f8ebc65cf522acb51`**,
`Tobia5/model-sync-starter`, branch `review/layered-refactor-qm-20261020`.
The later report-only commit is not claimed as the tested source. No implementation,
criterion, policy, deadline, platform, resource cap or test selection changed.
Tested86488c4/reporta0f46d7, failed full reportb7f3cb5 and all20 earlier archives
remain immutable. Earlier QM sections below retain their historical results.

## Actual commands, identities and original exits

| Execution | Measured result | Original exits |
|---|---|---|
| `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify` | PASS; run `c965b470-292f-4346-b739-00048a7163f5`; 222 JUnit /102 Python /41 XML; zero failures/errors/skips; 222.625s build supervision | build0, qualification0, execution audit0, collection0 |
| Git Bash `scripts/test-triggers.sh` |86 core /43 trigger standalone assertions PASS, separately counted from JUnit |0|
| Archived `python capacity.py full`, after default build/collection/contracts | PASS; attempt `ca712387-37bd-4267-aaf4-552f5a19de1d`; host21.316/21.266/21.334GiB >= unchanged18GiB; Docker19.487GiB available; C77.679/D1069.773GiB free |0|
| `python scripts/full-oracle-container.py` | PASS; full run `751ea112-4068-48a0-97fc-c12605a59802`; 6569.171s wrapper supervision / 6568.02s infrastructure | wrapper0, nested Maven0, qualification0, collection0 |

Exactly one full attempt; no unchanged retry. Invocation-only acknowledgments:
`MODELSYNC_ORACLE_CONTAINER=EXPLICIT_DISPOSABLE`,
`MODELSYNC_FULL_ENV_ACK=DISPOSABLE_NONPRODUCTION`, and
`MODELSYNC_MAVEN_SETTINGS=.mvn/qualification-central.settings.xml`.
The wrapper's Maven command retained `-B -ntp -s <approved-project-settings>
-fae -Pfull clean verify`; no diagnostic selector, skip flag or extra suite filter.
The profile retains the one reviewed characterization-tag exclusion.
Settings SHA256 `ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`.
Infrastructure attempt `db2cb07f-85c0-4fb1-af87-a8541223bf73`.

The matching default run c965b470 has its own archived marker and reports before
the full clean. The full run has a
new marker and fresh224 Surefire tests/43 XML reports, including2 full-only tests,
plus **83 Oracle methods across18 suites**, zero failures/errors/skips.
All83 method names match the mandatory inventory. All default/full architecture,
profile separation, runtime-policy, migration, atomicity, fencing, recovery,
shutdown, transport, quota, creation, accounting, mutation and load gates passed.
No rejected/stale evidence. The machine full report labels a separate default
command NOT_RUN in its own marker; the independent matching default is the
actual c965b470 run, not an attribution of old XML to the full marker.

## Named target and actual target fixture

Only **`oracle26-nowait-ledger-first-v1`** is qualified: Oracle26ai23.26.3 / JDBC23.26.3.0.0,
point-lock-policy ORACLE_NOWAIT, quota-lock-policy ORACLE_NOWAIT,
insert-policy ORACLE_26_NOWAIT, qualified JPA ORACLE_NOWAIT mapping,
DURABLE cooldown/schema-extension version1, supported ledger-first writers and
correctly enlisted local datasource/JpaTransactionManager.
Twenty fresh validated receipts cover all five required roles: starter-target,
nowait-atomicity, recovery-host, actual-worker and cooldown-worker. These verify
actual adapter settings, consumed mapping and datasource enlistment, not only
property values. Versioned migration and all current integration controls passed.
Compatibility controls remain; DEFAULT, older Oracle and arbitrary replacement
beans are not granted these bounded-wait guarantees by this result.

The repaired fixture executed in this full run: committed A/validatorA/revision0/
generation1, then changed-B/validatorB/revision1/generation2; both paired state
outcomes APPLIED/IDLE. Two owner reads, changed digest, validatorA propagated to
the second request; five changed-phase SQL statements and zero capture overflow.
Actual existing-row SQL included:

```sql
select ce1_0.storage_key from sample_customer ce1_0
where ce1_0.storage_key=? and ce1_0.local_version=?
for update of ce1_0.storage_key nowait
```

No historical diagnostic report substitutes for this current source/run proof.

## Original full worker method: all ten phases PASS

Actual auto-configured ReactorSyncWorker: four lanes, pool8, persistence4/owner8
threads, queue64 per thread, poll250ms, owner spacing20ms/latency5ms.
Fixed productive/max-idle250ms; adaptive productive5ms/max-idle1s. Original
**160 measured completions within20 seconds** retained for both productive phases,
with10 warmup each. Full JVM trace property absent/default false; this is the
original full method, not the traced four-cell diagnostic launcher.

| Phase | Result | Measured ms | Committed / owner calls | Ticks / empty | Wakeup upper ms |
|---|---|---:|---|---|---|
| fixed-productive | PASS | 16381 | 160 / 160 | 223 / 63 | - |
| fixed-empty | PASS | 4013 | 0 / 0 | 60 / 60 | - |
| bounded-productive | PASS | 9904 | 160 / 160 | 206 / 46 | - |
| bounded-empty | PASS | 4014 | 0 / 0 | 16 / 16 | - |
| bounded-future | PASS | 4015 | 0 / 0 | 16 / 16 | - |
| future-becomes-due | PASS | 895 | 1 / 1 | 4 / 3 | 395 |
| local-urgent | PASS | 894 | 1 / 1 | 4 / 3 | 894 |
| independent-process-urgent | PASS | 1918 | 1 / 1 | 8 / 7 | 809 |
| cooldown-independent-owner-and-recovery | PASS | 5848 | 1 / 1 | 29 / 27 | other 39 / recovery 859 |
| cooldown-expires | PASS | 2951 | 2 / 2 | 21 / 19 | 826 |

All ten native counter intervals have VALID eight-session continuity. Empty/future
phases consumed no owner calls, grants or denials. Urgent/due/cooldown/recovery
wakeups remained within2500ms; unrelated-owner progress was observed.

**The historical worker timeout remains FAILED and unexplained.** This current
full regression PASS is not evidence of a repaired production cause. No pacing
change, deadline increase or provisional fixed-control criterion adoption occurred.
Prior virtual controls and the five focused diagnostic passes keep their original
source/scope. This full used its own83 methods and all ten worker phases.

## Mutation and load evidence

PIT1.25.9/provider1.2.3 on resolved JUnit6.0.3 completed in7s: **167 KILLED,
1 SURVIVED /168 generated =99.40% killed/generated**; NO_COVERAGE/TIMED_OUT/
RUN_ERROR/UNKNOWN all0. Existing85% gate, eight behavioral classes and four target
test classes unchanged. XML/HTML and scope/effective configuration retained.
The survivor removes `FreshnessPolicy.positive(maxAge)` at line19; source bytes
match b7f3cb5. Existing acceptance-equivalence review remains: positive refresh
plus refresh<=max rejects nonpositive max; null comparison rejects null. Exception
ordering/messages may differ. It stays SURVIVED and included, with no exclusion
or score adjustment; this is not a universal semantic-equivalence claim.

Six explicitly selected **in-memory semantic Gatling scenarios PASS**,367 HTTP
requests/0 errors, with owner/ledger assertions and raw HTML/logs. HTTP acceptance
is not synchronization completion. These short synthetic scenarios have0ms
reported warmup and are not Oracle-scale or production SLO qualification.
Per-scenario latency/dataset/counter tables are in the detailed report.

Oracle capacity: **1,000,000 seeded rows /100k due**, seed4,439,182ms; four direct
`runOne` workers with5ms loop sleep, pool8, controlled owner20ms spacing, ordinary5ms/
slow25ms latency and injected failures. **200 measured committed reconciliations
from220 physical owner calls in15,624ms** (210/230 with warmup);12.8008 syncs/s versus
14.0809 owner calls/s, not HTTP accepted-request rate or actual Reactor-worker
throughput. RunOne p50/p95/p99=5/62/78ms; claimed-only63/80/102ms. Lag p50/p95/p99=
4,465,922/4,471,492/4,472,018ms includes seed time: the common requested epoch is
recorded before seeding. It is not steady-state latency or a million completions.
Native session/cursor work, waits, plans, seed/warmup/measurement/drain/cleanup,
projection outcomes and bounded pool/queue observations remain raw artifacts.

OracleScaleIT passed all eight distributions:100k fixture, one ledger claim37ms,
persisted lease/token/generation/lockValid checks. This is not provider admission.
The eight controlled comparison variants passed with the same ORACLE_NOWAIT quota,
ORACLE_26_NOWAIT insert and DURABLE policies: **2916 observed acquisitions/claims =
1766 grants +712 denials +438 confirmed refusals**;0 active/unknown/uncertain outcomes,
1766 owner calls/1604 measured reconciliations. All32 lane runs ended normally.
104 logical cooldown requests reached APPLIED,104 durable accept commits;
115 physical materialization attempts =104 commits +11 refused rollbacks,0 unresolved
or logical publication failures. Historical unclassified55 and five earlier
publication failures remain unchanged; no arithmetic remainder relabelling.

## Environment, resources and independently verified cleanup

JDK21.0.12.1 / Maven3.9.9 / Wrapper3.3.4 / Python3.12.10 / Boot4.1.1 /
JUnit6.0.3 / Hibernate7.4.5.Final / Surefire-Failsafe3.5.3 / ArchUnit1.5.0 /
Gatling3.13.1/plugin4.11.0 / Docker29.8.2. Actual PIT execution verifies the pinned
provider/platform combination; no platform change was used to obtain PASS.
Pinned image `ghcr.io/gvenzl/oracle-free:23.26.3-slim@sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`.
Oracle6GiB/4CPU cap, generated per-run credentials, separate runtime/migration
schemas, loopback-only publication and the18GiB admission were unchanged.

Runner reported16 available processors. Oracle test JVM observed maximum heap
16,944,988,160 bytes and used-heap snapshot289,356,592 bytes; that snapshot is not
peak usage or an enforced8GiB heap cap. Gatling maximum heap1,073,741,824 bytes.
The8GiB Maven/PIT/Gatling planning allowance in admission is not an assertion that
every ergonomic JVM maximum is capped there. Production memory/resource bounds
remain an operations gate; no global JVM/resource setting was changed here.

Wrapper cleanup PASS; independent checks confirm owned container
`msq-526fee3f3fe2`, volume `msq-526fee3f3fe2-data` and all12 JVM PIDs from20
current-run receipts absent. Docker inspect exit1 meant explicit no-such-object/
volume, not an ignored Docker-service error. All raw PIT13 files and Gatling192
files, including HTML assets, are archived. No unrelated resource was altered.
All20 earlier archives and the complete old QM suffix remain unchanged.

## Remaining production requirements

Configured full PASS is **not production acceptance**. Historical worker timeout
cause remains unknown. Independent plain-UPDATE cancellation remains historically
FAILED; its separate current characterization is NOT_RUN, with unchanged approved
applicability/body. No unsupported path or all-configuration guarantee is added.
Unrecorded-response/acceptance windows, uncertain outcomes, receipt retention and
maintenance, security, operations, consumer/supply-chain/provider integration and
real production capacity/SLO gates remain open under the known-ID owner-to-Oracle/
JPA first-release scope. No parent release gate is closed merely by this run.
No new features, merge, main change, workflow bootstrap or package publication.

[Root QM](../../../QM.md).
The root QM retains all prior historical content unchanged.

---

Archive SHA256 `917cc99ba0f0f455f811b061e12801f3c4d3a261cc56de562ddb21b0972dd63c`; 5,036,287 bytes / 550 entries.

[Integrity receipt](report-verification.json): CRC and entry digests passed;
all 20 prior archives, the historical QM suffix and the original generated full
QM/evidence remain unchanged. Post-report docs, architecture and diff checks passed.

## Current Oracle method inventory (all PASS)

Every method name below comes from the fresh current-run XML and matches the
unchanged mandatory manifest. No earlier focused result supplies a missing method.

| Suite | Required / passed | Methods |
|---|---:|---|
| OracleSmokeIT | 1 / 1 | `claimAndFencingOnOracle` PASS |
| OracleConcurrencyIT | 3 / 3 | `reclaimedLeaseRejectsOldToken` PASS<br>`competingConnectionsCoalesceOneLogicalWorkItem` PASS<br>`hintsNeverSpendPermitsAndIndependentGatesStillSerialize` PASS |
| OracleMigrationIT | 1 / 1 | `v1ToV2PreservesWaitingAndDeadEvidence` PASS |
| OracleScaleIT | 1 / 1 | `dueLedgerClaimHasMeasuredDatabaseWork` PASS |
| OracleCapacityIT | 1 / 1 | `millionRecordReconciliationMeasuresCompletedWork` PASS |
| OracleSelectionIT | 11 / 11 | `availableBindingProgressesPastDelayedEarlierWork` PASS<br>`lockedPrefixDoesNotHideWorkBeyondDriverFetch` PASS<br>`emptyAndFutureQueuesDoNotLease` PASS<br>`recoveryAndWaitingBothProgressUnderRepeatedExpiry` PASS<br>`waitingOrderAndFutureBoundaryArePreserved` PASS<br>`lockedEarliestDoesNotHideLaterUnlockedWaiting` PASS<br>`availableBindingsAlternateWithinOnePriority` PASS<br>`simultaneousWorkersNeverClaimTheSameLease` PASS<br>`filteringWaitingWorkNeverSuppressesRecovery` PASS<br>`lockedEarliestRecoverySkipsToLaterExpiredLease` PASS<br>`activeLeasesAndOtherPrioritiesAreNotClaimed` PASS |
| OracleOptimizationIT | 1 / 1 | `comparesIndependentChangesOnTheSameOraclePopulation` PASS |
| OracleCooldownIT | 5 / 5 | `durablePendingSurvivesForcedOwnedProcessTermination` PASS<br>`heldQuotaReleaseWithinBudgetReusesAbsoluteDeadline` PASS<br>`committedCooldownPrecedesFailedMetadataAndRecoveryDoesNotRefetch` PASS<br>`duplicateInsertRefusalIsUnrecordedUntilCompetitorCommitOrRollback` PASS<br>`budgetExhaustionRetainsSharedPendingAndOtherProviderProgress` PASS |
| OracleAtomicityIT | 7 / 7 | `successfulProjectionAndLedgerCommitTogether` PASS<br>`failureAfterProjectionFlushRollsBackBoth` PASS<br>`obsoleteLeaseCannotApplyProjectionOrLedger` PASS<br>`failureDuringJdbcLedgerWriteRollsBackJpaFlush` PASS<br>`changedLocalRevisionReschedulesWithoutOverwriting` PASS<br>`failureAtSpringCompletionRollsBackBoth` PASS<br>`failureInActualOracleCommitRollsBackBoth` PASS |
| NowaitOracleAtomicityIT | 7 / 7 | `successfulProjectionAndLedgerCommitTogether` PASS<br>`failureAfterProjectionFlushRollsBackBoth` PASS<br>`obsoleteLeaseCannotApplyProjectionOrLedger` PASS<br>`failureDuringJdbcLedgerWriteRollsBackJpaFlush` PASS<br>`changedLocalRevisionReschedulesWithoutOverwriting` PASS<br>`failureAtSpringCompletionRollsBackBoth` PASS<br>`failureInActualOracleCommitRollsBackBoth` PASS |
| OracleWorkerIT | 1 / 1 | `autoConfiguredWorkerPacesBacklogAndBoundsIdleRechecks` PASS |
| OracleRecoveryIT | 6 / 6 | `localRevisionAndOwnedFieldsSurviveCrash` PASS<br>`crashAfterOwnerResponse` PASS<br>`crashAfterDurableClaim` PASS<br>`crashAfterRealProjectionFlush` PASS<br>`newerRequestSurvivesCrashAndOldToken` PASS<br>`crashAfterCommitBeforeObservedSuccess` PASS |
| OracleIoIT | 11 / 11 | `stalledLoginCleansUpItsPhysicalConnectionAttempt` PASS<br>`unavailableDatabaseConnectionDoesNotWaitIndefinitely` PASS<br>`boundedStopDoesNotCallALockedJpaOperationDrained` PASS<br>`ownerSocketTimeoutEndsThePhysicalClientIndependentlyOfFetchTimeout` PASS<br>`lostCommitAcknowledgementDoesNotLoseCommittedCompletion` PASS<br>`gracefulStopDrainsARealOwnerSocketAndAtomicCompletion` PASS<br>`lateOwnerCannotBecomeASuccessOrAFalseDrain` PASS<br>`poolAcquisitionIsBoundedAndRecovers` PASS<br>`databaseReadStallEndsPhysicalCallAndPoolRecovers` PASS<br>`connectionLossBeforeCommitIsUncertainUntilFreshInspection` PASS<br>`transactionDeadlineRollsBackEarlierWrites` PASS |
| OracleLockPolicyIT | 7 / 7 | `disabledPolicyKeepsCompatibleSqlAndSuccess` PASS<br>`enabledProducerRefusalIsNeverAccepted` PASS<br>`firstInsertsRemainOrdinaryAndUncontendedFunctionalityWorks` PASS<br>`actualWorkerBoundsContentionRetriesWithoutForgettingOwnerReads` PASS<br>`enabledSuccessRetainsRefreshGenerationFencingAndLocalRevision` PASS<br>`resultRefusalRollsBackBeforeDurableRetryAndRespectsCooldown` PASS<br>`lockedRetryMetadataIsReportedOnceAndLeavesLeaseRecoverable` PASS |
| OracleQuotaContentionIT | 6 / 6 | `existingRegistrationAcrossInstancesDoesNotChangeAdmissionOrSpacing` PASS<br>`failedCooldownAfterOwnerReadPropagatesOnceAndKeepsRecoverableLease` PASS<br>`heldAdmissionRefusesRepeatedlyWhileAnotherInstanceAndGroupContinue` PASS<br>`compatibleDefaultStillCommitsSharedAdmissionAndCooldown` PASS<br>`heldCooldownIsNotReportedPersistedAndNeverShortensPriorCooldown` PASS<br>`coordinatorNeverCallsOwnerWithoutCommittedAdmissionAndRecovers` PASS |
| OracleFirstCreationIT | 12 / 12 | `nativeWorkCommit` PASS<br>`firstProjectionAndStateRollbackAreFenced` PASS<br>`defaultWorkCommit` PASS<br>`firstProjectionAndStateCommitAreFenced` PASS<br>`defaultWorkRollback` PASS<br>`nativeQuotaRollback` PASS<br>`nativeQuotaCommit` PASS<br>`nativeWorkRollback` PASS<br>`defaultQuotaRollback` PASS<br>`nativePassiveCreationCommit` PASS<br>`nativePassiveCreationRollback` PASS<br>`defaultQuotaCommit` PASS |
| OracleQualificationTargetIT | 1 / 1 | `namedTargetConsumesEveryPolicyAndQualifiedMapping` PASS |
| OracleCooldownWorkerIT | 1 / 1 | `pendingPublicationDoesNotLoseLanesAndIndependentProviderCompletes` PASS |

## In-memory Gatling measurements (all PASS)

HTTP and owner/ledger counters remain separate.0ms warmup is the reported fixture
configuration. These are short semantic checks, not sustained capacity/SLO proof.
`completed_rows` enumerates the harness ledgerKeys list, populated by the leases
fixture; zero in other modes is not a census of single-key reconciliations.
Owner-call counters cover the fixture lifetime, including preparation; scenario
assertions separately prove fresh visits add no fetch and preserve age/retention.
No HTTP202/request count is counted as a completed synchronization.

| Mode | HTTP requests / errors | HTTP p50 / p95 / p99 ms | Accepted visits / job triggers | Raw owner calls | Synthetic ledgerKeys / ledger rows / reported completed_rows | Measurement+drain ms |
|---|---|---|---|---:|---|---:|
| cooldown | 3 / 0 | 7 / 39 / 39 | 0 / 2 | 2 | 0 / 1 / 0 | 765 |
| event | 4 / 0 | 3 / 28 / 28 | 1 / 0 | 2 | 0 / 1 / 0 | 778 |
| fresh | 40 / 0 | 94 / 227 / 227 | 40 / 0 | 1 | 0 / 1 / 0 | 871 |
| hot | 100 / 0 | 280 / 286 / 288 | 100 / 0 | 1 | 0 / 1 / 0 | 1088 |
| leases | 200 / 0 | 354 / 368 / 376 | 0 / 200 | 101 | 200 / 200 / 101 | 967 |
| unsafe | 20 / 0 | 95 / 97 / 97 | 20 / 0 | 0 | 0 / 1 / 0 | 1502 |

Leases accepted200 job triggers and reported101 completed ledgerKeys; this is not
200 completed synchronizations. All per-scenario projection/ledger assertions and
HTTP assertion XML passed. Oracle workload200 measured completions is a separate
real-Oracle measurement, not derived from these HTTP responses.

## Raw artifact inventory and provenance

- `frozen-source.json`, `frozen-source-end.json`, `qualification-target.json`, `full-inventory-results.json`: exact source/tree,20 prior archive seals, complete83-method mapping and actual ten phase results.
- `default-*/`: matching default c965b470 command/log, original exits, all95 fresh raw files, marker, XML, audit and generated QM. This is not borrowed into the full marker.
- `full-*/`: the single wrapper command and result, original30MB build log, separate qualification/collection logs, all417 fresh raw files and their manifest. Includes original run-scoped QM/evidence JSON,83 Oracle XML methods, current A/B SQL, all runtime receipts, faults, native plans/counters/waits and complete PIT/Gatling HTML assets. The generated full machine report is preserved verbatim, not overwritten by this human summary.
- `result-summary.json`, `human-summary.md`: compact current results/status; report-only commit is not falsely claimed as tested code.
- `comparison-complete-observation.json`: observed boundary acquisitions and committed claims reconciled independently; logical cooldown requests versus accept/apply commits/refusals; no arithmetic remainder classification.
- `full-partial-pit-observation.json`, `running-observation.json`, `run-health-observations.jsonl`: timestamped observations while the run was running. Their RUNNING/partial state is historical progress, not a final qualification claim.
- `survivor-review.json`, `toolchain-observation.json`: raw surviving mutation remains counted, source acceptance review unchanged, actual JDK/JUnit and pinned tool declarations; all later runtime reports remain present.
- `scoped-capacity-*.json`, `capacity-command.*`, `owned-infrastructure-observation.json`, `cleanup-and-preservation.json`: one18GiB admission, actual pinned container caps/mount/loopback,20 receipts/12 PIDs, explicit absent-resource responses and prior-evidence preservation.
- `session.py`, `capacity.py`, `publish-report.py`, `standalone-contracts.*`: exact external orchestration/reporting and86/43 offline assertions; no product implementation change.
- `nonsecret-artifact-check.json`, `raw-archive-manifest.json`, `SHA256SUMS`: bounded generated-credential-format check and all raw entry/archive hashes. This is not general security certification.

The normal/default and full runs have different fresh markers for the same clean
source. The full includes its own224 Surefire tests and all83 Oracle methods;
previous86488c4/c8b6153a diagnostic5/5 remains separate historical evidence.
Original failed b7f3cb5, unclassified55, earlier publication failures and all older
results retain their exact scope/status. Historical worker timeout is unexplained;
current regression PASS is not a causal fix. No new criterion proposal is adopted.

No source edit or concurrent build occurred during full verification/collection.
No additional full run, merge, main change, workflow bootstrap or publication.
