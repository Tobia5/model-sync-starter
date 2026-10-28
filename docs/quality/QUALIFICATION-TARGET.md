# Accepted named Oracle qualification target

The owner explicitly approved this applicability mapping on 2026-10-24, after
accepting first-creation source `cfcc53d` / report `5819b80`. This decision is
neither merge/publication authorization nor production acceptance. Earlier
source-bound reports and the plain-UPDATE cancellation failure remain unchanged.

## Target and executable inventory

`oracle26-nowait-ledger-first-v1` means the pinned Oracle26ai / 23.26.3 database
and JDBC23.26.3.0.0, Hibernate7.4.5.Final, with the four accepted lock/insert options plus explicit durable publication:

| Property | Required value |
|---|---|
| model-sync.jdbc.point-lock-policy | ORACLE_NOWAIT |
| model-sync.jdbc.quota-lock-policy | ORACLE_NOWAIT |
| model-sync.jdbc.insert-policy | ORACLE_26_NOWAIT |
| model-sync.jdbc.cooldown-policy | DURABLE, extension version1 |
| model-sync.jpa.lock-policy | ORACLE_NOWAIT, consumed by the qualified mapping/store |

The [machine-readable inventory](qualification-target.json) names every mandatory
Oracle test method (the original77 retained plus6 cooldown methods,83 in18 suites), the exact optional characterization,
qualified platform, policies and deliberate compatibility controls. The default
source audit and effective-POM audit enforce that selection. Full validation
requires exact discovered method inventories, no failed/skipped cases, fresh
source/run-bound runtime receipts and all unchanged raw evidence gates.

`OracleQualificationTargetIT.namedTargetConsumesEveryPolicyAndQualifiedMapping`
inspects the actual adapters, qualified mapping, platform and datasource/JPA
transaction-manager enlistment, then uses the public gateway/coordinator and
checks generated Hibernate NOWAIT SQL plus paired committed rows. Runtime receipts
also verify actual continuous-worker, independent-process recovery and NOWAIT
atomicity hosts. Inspection is test-only; no production reflection or global
datasource settings are introduced. Fallback to DEFAULT or arbitrary replacement
beans/mappings cannot supply named-target evidence.

## Accepted mechanism to test mapping

| Path / requirement | Required integration evidence |
|---|---|
| Existing MS_WORK point locking, failed enqueue not accepted | OracleLockPolicyIT.enabledProducerRefusalIsNeverAccepted |
| Qualified mapping lookup / retained-lock refresh, generation/revision/fencing | OracleLockPolicyIT.resultRefusalRollsBackBeforeDurableRetryAndRespectsCooldown; enabledSuccessRetainsRefreshGenerationFencingAndLocalRevision |
| Rollback before bounded LOCAL_CONTENTION; failed metadata write once, recoverable lease | OracleLockPolicyIT.lockedRetryMetadataIsReportedOnceAndLeavesLeaseRecoverable; actualWorkerBoundsContentionRetriesWithoutForgettingOwnerReads |
| Existing quota acquire/cooldown, other groups and fresh recovery | All six OracleQuotaContentionIT methods, including failedCooldownAfterOwnerReadPropagatesOnceAndKeepsRecoverableLease |
| Explicit/passive first work and first quota INSERT, competitor commit/rollback | Six native OracleFirstCreationIT methods |
| First projection/state creation serialized by work fence, paired commit/rollback, newer revisions | OracleFirstCreationIT.firstProjectionAndStateCommitAreFenced; firstProjectionAndStateRollbackAreFenced |
| All seven local atomicity requirements | OracleAtomicityIT compatibility control and NowaitOracleAtomicityIT named configuration |
| Remaining shutdown, transport, uncertain COMMIT, independent-JVM recovery | All eleven mandatory OracleIoIT methods and all six OracleRecoveryIT methods |

Covered locking/insertion requires database refusal with the competitor still
unresolved, physical return, whole-attempt rollback, preserved intent and healthy
or disposed resources. Projection/state creation requires work-fence serialization,
paired committed outcomes, fencing and local revisions. Independent client
cancellation/ORA-01013 is not an additional mechanism for these covered paths.

## Exact characterization applicability change

Only `OracleIoIT.realStatementLockWaitCancelsAndRollsBack` carries
`modelsync-driver-characterization`; the full profile excludes that literal tag.
Its original body, plain UPDATE, deadlines and assertions are hash-guarded against
report `5819b80`. No class is excluded or disabled. All eleven other I/O cases
remain mandatory. Explicit execution is available with
`python scripts/full-oracle-container.py --statement-characterization`; it is a
separate diagnostic, not a full qualification, and a failure keeps its nonzero exit.
It is not executed by the integrated named run.

Historical independent cancellation is **FAILED**, source `2c39b75`, report
`43040e7`; network containment did not qualify server termination and recovery
needed fixture cleanup. Current-run characterization is **NOT_RUN** unless it
actually executes with current source/run evidence. No fresh PASS is inferred.

The retained locked-JPA shutdown test deliberately uses the original DEFAULT/WAIT
mapping to create a physical stall, with all original shutdown/resource assertions.
That is a mandatory failure-handling control, not bounded-locking evidence or a
support promise for DEFAULT. Legacy coalescing, quota and atomicity controls and
the four observed DEFAULT first-insert waits are similarly explicit in the manifest.

## Limits and release gates

[SUPPORTED-WRITERS](SUPPORTED-WRITERS.md) governs starter tables and business
writers: concurrent compliant instances, ledger first, one correctly enlisted
local datasource/JpaTransactionManager. The qualified sample/recovery mappings
use one projection table and retain its lookup lock through refresh/flush. This
does not qualify arbitrary extra flush targets, constraints, triggers, replacement
beans or custom SQL. An unprotected equivalent wait in a supported path remains a
blocker. DEFAULT stays compatible and makes no equivalent bounded-wait promise;
older Oracle lines are not this target.

Every other applicable gate remains: transport/network/server lifecycle,
uncertain COMMIT, shutdown, recovery, fencing, quota, first creation, architecture,
85% PIT scope, semantic Gatling and Oracle capacity assertions. A requested
cooldown extension that failed to commit is still unpublished; provider-wide
publication/recovery requirements remain open. Security, operations, consumer
compatibility and other parent release gates stay open. A configured full PASS
does not establish complete bounded persistence or production readiness.

## Cooldown publication extension

The current implementation adds opt-in DURABLE publication to the same named
configuration. [Ordering/retry contract](COOLDOWN-PUBLICATION.md) and the explicit
schema extension apply to its standard coordinator path. `OracleCooldownIT` adds
five small refusal/restart/metadata cases, `OracleCooldownWorkerIT` adds the actual
worker case, and the existing migration method verifies extension preservation.
All original77 methods and independent-characterization applicability are retained.
This is an implementation and executable inventory; current measured results belong
to the source/run-specific QM report. It does not close the unrecorded-response,
uncertain-outcome, retention, security or operations release gates.
