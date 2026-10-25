## Durable cooldown publication milestone - 2026-10-25

Opt-in DURABLE adds a separate receipt/admission protocol to the accepted point-lock
scope. [Exact report and original failures](../../validation/qualification/2026-10-25-cooldown-publication/README.md).
Small Oracle source5b96b47/run943ef32d-9474-40db-8887-e92df5b880b8 passed7/7;
comparison source1cfbefb/run6edac4cd-17e1-44c9-bd34-d7e8657d31b1 passed8/8.
Current integrated full is BLOCKED at unchanged18GiB admission, all83 methods/PIT/
Gatling NOT_RUN. These results do not reopen accepted queue/pacing/atomicity or
cancellation applicability decisions, nor qualify complete bounded persistence.

| Path | Demonstrated scope / remaining limit |
|---|---|
| MS_COOLDOWN first acceptance | Native Oracle26 INSERT NOWAIT;8 refusals while duplicate competitor remained unresolved, commit/rollback alternatives, canonical identity/deadline. Failed acceptance is not shared acceptance. |
| Existing quota plus receipt materialization | Quota first, exact receipt second, both NOWAIT; four known-refusal retries in fresh transactions with jitter outside resources. One refusal then success85ms; four refusals then durable pending123ms. Exact SQL/server LOCK_WAIT0, rollback/TX0 and healthy pool observed with competitor held. |
| Cross-instance admission / pending restart | Committed pending maximum is checked under quota fence. New admission refused/denied; other provider progressed. An already-in-flight admission may complete. Owned JVM died after committed pending acceptance; fresh independent gate observed/recovered, exact child server session absent. |
| Work metadata after cooldown commit | Real JDBC work lock refused after APPLIED receipt committed; fresh rows showed recoverable LEASED intent/no success state. Canonical retry used one owner read and no deadline drift. |
| Actual Reactor/JPA/JDBC worker | COOLDOWN_PENDING persisted, independent provider completed, worker remained running;8ms DRAINED with physical0/pool0 before competitor release, later recovery without another owner read. |
| Controlled load |104 logical cooldowns all APPLIED,10 physical refusal rollbacks,0 unknown/unresolved publication outcomes; all four lanes finished in each of eight variants. Acquisition refusals469 versus historical102 remain visible cost. |
| Unrecorded response / uncertain completion | Still a release limitation. An unaccepted response is not known to other nodes; unexpected/uncertain completion is not retried as confirmed refusal. APPLIED retention/operations and all other release gates remain open. |

The complete first-work/quota/projection/state and extra-flush/transport coverage
below remains in force. Historical plain-UPDATE cancellation FAILED remains; current
characterization NOT_RUN. DIRECT/older platforms/arbitrary replacement beans are not
qualified by DURABLE evidence. No million-row seed occurred in focused development.

## Current accepted named configuration - 2026-10-24

The owner approved [QUALIFICATION-TARGET](QUALIFICATION-TARGET.md) and its exact
[executable inventory](qualification-target.json). It supersedes only the earlier
pending/applicability statements for the named Oracle26 NOWAIT/INSERT configuration.
All other applicable requirements and the historical plain-UPDATE cancellation
FAILED result remain. An unexecuted current characterization is NOT_RUN.
DEFAULT, older Oracle lines and arbitrary replacement mappings are not qualified
by this decision. Failed cooldown publication/recovery and other parent release
gates remain open. The earlier text below is preserved as decision history.

# Opt-in Oracle point-lock coverage

## First-creation result - cfcc53d

Clean source `cfcc53d9bb7a90e20bbd67f4255d8322c1fc0d99`, tree
`1a900bb8845c912793fb5136118222a1fac84c8e`: default run
`73cbe204-8173-4287-a77c-d294ff1623d8` PASS/155 JUnit/79 Python/exit0;
focused run `16a9d2a6-3581-4715-a9d5-895875d3e34e` PASS/12 of12/exit0.
Six native INSERT refusals returned in8-75ms with A unresolved. Four DEFAULT
INSERT wait observations are not bounded-policy PASS. Full is NOT_RUN; original
failure/gate unchanged. [Raw evidence](../../validation/qualification/2026-10-24-first-creation/README.md)
and [entry/transaction/competitor audit](FIRST-CREATION-REACHABILITY.md).

| Legitimate first path | Current classification | Actual test/outcome and limit |
|---|---|---|
| MS_WORK explicit enqueue | **Protected by measured conflict policy** when insert-policy=ORACLE_26_NOWAIT | nativeWorkCommit/nativeWorkRollback: actual INSERT refuses54 before A resolution, whole B attempt/earlier guard intent rolled back, no accepted ticket. Fresh retry after A commit gives generation2; after rollback generation1. One target row/urgent forced intent preserved. DEFAULT remains an observed ACTIVE/TX WAITING insert until A resolves, not bounded. |
| MS_WORK passive first enqueue | **Protected by measured conflict policy** with same option | nativePassiveCreationCommit/nativePassiveCreationRollback through ModelSync.requestRefresh: distinct passive INSERT refuses54. After A commit generation1/force1 remains; after rollback B creates generation1/force0. DEFAULT passive INSERT is not qualified bounded. |
| MS_QUOTA first registration | **Protected by measured conflict policy** with same option | nativeQuotaCommit/nativeQuotaRollback through actual register (the startup method): independent B transaction rolls back/refuses54 before A resolves. Fresh retry gives one row/spacing17/next_allowed0; mismatch18 rejected. DEFAULT waits. Tests call real registration after startup; not whole multi-pod startup/restart qualification. |
| Projection first creation | **Serialized by demonstrated protocol** | firstProjectionAndStateCommitAreFenced / firstProjectionAndStateRollbackAreFenced: actual finish flushes JPA insertion under MS_WORK. B worker cannot claim A's lease; ledger-first business writer refuses before mapping/DML. Fresh connections see both projection/state on A commit, neither on rollback. B local revision/new generation and later recovery retained. No native JPA rewrite. |
| MS_STATE first creation | **Serialized by demonstrated protocol** | Same two tests execute completeOnDemand/currentToken/UPDATE-then-INSERT under work fence; paired commit/rollback and later single state row, obsolete token rejection. No out-of-protocol state writer or fabricated shared lease. |

The measured combination uses existing work/quota NOWAIT, a capable JPA mapping,
and explicit Oracle26ai/JDBC23.26 INSERT capability for work/quota. No default/older
configuration receives a new bounded guarantee. Additional mappings/constraints/
flush targets, transport, uncertain COMMIT and failed-cooldown publication remain
separate requirements. The original plain-UPDATE failure/full gate still applies
to unprotected operations. A passing first-creation regression is not blanket gate
acceptance, complete bounded persistence or production readiness.

## Historical b280af4 coverage (preserved)

The first-creation table above supersedes the earlier UNQUALIFIED entries below
within its precise configuration. Earlier existing-row measurements remain accepted.

**Current scoped decision and quota evidence:** the owner accepted the existing
work/JPA subset at2eea1d8; [decision-to-test mapping](LOCK-POLICY-DECISION.md).
New clean source `4057535359400242c3a20d69f7ad06197caee5e6`, tree
`4bfbfaea40f5413fe3bd0067b7f9b9047bfcfb1e`, passed default149 JUnit/76 Python,
run `50f38395-84ab-4b90-973d-6c7a6c154280`, and focused OracleQuotaContentionIT6/6,
run `35ead9f5-f6af-4c49-a323-4ad149737b85`. Eight refusals across two independent
starter applications returned in8-30ms including transaction/observer/dispatch
overhead; exact sessions had LOCK_WAIT0 with blockers held, guard writes rolled
back, TX_ACTIVE0 afterward and healthy pools. Two reconciliations used three
synthetic reads. This is new quota regression evidence, not full/release acceptance.
[Raw reports](../../validation/qualification/2026-10-24-quota-contention/README.md)
preserve actual SQL/counters/timings. The measurements below for2ee remain historical.

## Implementation and qualification boundary

`JdbcWorkStore(jdbc)` and `JpaProjectionStore(em,mapping)` keep compatible defaults.
The new explicit `ORACLE_NOWAIT` options affect existing-row point SELECT locking
and JPA pessimistic lookup/refresh only. There is no production SQL inspector,
native INSERT/UPDATE extension, datasource timeout change or automatic schema DDL.
The JDBC option rejects a non-Oracle datasource. JPA mappings must implement
`LockingJpaProjectionMapping`; the store rejects a legacy mapping at construction
when opted in. Implementations explicitly promise per-operation options and tenant
scoping; provider-generated SQL still needs real integration qualification.

The accepted prototype at `eb18209`, report `baba54c`, is immutable historical
evidence. New integration uses actual starter beans and adapters, not that prototype.
Current integration results belong in [QM](../../QM.md), with exact source/run/tree.
Tests and collectors below are implemented; no unexecuted case is claimed PASS.

**Final measured source:** `2eea1d849dfd8d5f7f0ee1ee6f3386412b282477`, tree
`8338eff0badecc4be2b8855f1e4e9fedbc8da0d6`. Default
`fb25cfb1-075e-407a-8ca2-8bfb276278b3` PASS/141 JUnit/72 Python/exit0;
focused Oracle `d7f2d3fe-348c-41a5-89b3-0f7ed0785200` PASS/21 of21/exit0:
seven production cases plus the same seven atomicity assertions in each of
DEFAULT and ORACLE_NOWAIT. Fresh detailed-suite evidence passed validation;
[raw attempts](../../validation/qualification/2026-10-24-lock-policy/README.md)
retain the initial5/7 failure and intermediate14/14 pass separately.
Full remains NOT_RUN, the original cancellation gate unchanged, production NO-GO.

| Reachable path | Production option | Implemented verification / remaining boundary |
|---|---|---|
| Existing MS_WORK record: enqueue/passive enqueue, lease validation, currentToken/completion/reschedule | Exact point `SELECT ... FOR UPDATE NOWAIT`; Oracle54 becomes LocalContention | PASS within the final scoped fixture: actual producer/result/metadata refusals while blocker held, exact-session state, rollback/guard release, healthy pool and durable intent. Queue SQL unchanged. |
| First MS_WORK insertion | Ordinary INSERT unchanged; empty NOWAIT SELECT does not reserve a missing key | Normal first-insert functionality test only. Concurrent unique insert wait is **UNQUALIFIED**, not bounded by this option. Existing duplicate-key reread is not a lock deadline. |
| Existing projection lookup | Explicit mapping capability and policy properties; sample uses per-call em.find | PASS for sample mapping: actual versioned NOWAIT lookup refused54, no projection DML continuation; fresh projection before/no success state. No silent legacy fallback. Custom mappings require their own SQL proof. |
| Existing projection refresh | Store passes policy properties to the actual em.refresh operation | PASS for retained-lock scope: default verifies per-operation properties/no global setProperty. Pinned Hibernate uses READ when lookup already holds the exclusive lock; real refresh emitted a plain SELECT. Independent observer refused54 immediately before flush, proving lock retention through refresh. Independent contended refresh on this production mapping is not a separate end-to-end experiment; the earlier isolated prototype remains distinct. |
| Missing projection | Ordinary persist/flush; no existing row to lock | PASS for uncontended first creation; concurrent unique/FK insert bounds **UNQUALIFIED**. Oracle26 native DML prototype is separate and is not shipped. |
| First MS_STATE reconciliation record | Ordinary UPDATE-then-INSERT under work-record fence | Normal completion and retained atomicity tests. Compliant writers must serialize per key through MS_WORK; independent-instance first-state proof remains open, not inferred from known owner IDs. Out-of-band starter-table writers are outside the [supported writer model](SUPPORTED-WRITERS.md); no universal INSERT wait bound is claimed. |
| Existing MS_STATE update | Ordinary UPDATE, serialized for compliant workers by MS_WORK lock | Fencing/atomicity retained; arbitrary noncompliant writers, triggers and extra locks are not covered. |
| Quota registration | Existing consistent read / ordinary INSERT and duplicate-key reread | New registration-boundary case verifies second-instance existing registration preserves spacing/cooldown and rejects conflict. Concurrent first-registration INSERT bound remains **UNQUALIFIED**, including compliant instances. |
| Quota acquire/cooldown update | Independent explicit quota-lock-policy=ORACLE_NOWAIT on existing-row SELECT only; ordinary UPDATE after exclusive lock | OracleQuotaContentionIT heldAdmissionRefusesRepeatedlyWhileAnotherInstanceAndGroupContinue / heldCooldownIsNotReportedPersistedAndNeverShortensPriorCooldown:3 refusals each while blocker held, exact sessions, guard rollback, pool health, other group and recovery. coordinatorNeverCallsOwnerWithoutCommittedAdmissionAndRecovers verifies zero owner calls without admission and bounded LOCAL_CONTENTION retry. failedCooldownAfterOwnerReadPropagatesOnceAndKeepsRecoverableLease verifies one propagated refusal, no claimed persistence/refund, recoverable lease, repeated read and later completion. Registration, transport and uncertain COMMIT remain separate gaps. |
| Extra flush targets / mapped tables / cascades / triggers | Ordinary provider SQL; no SQL rewriting | **UNQUALIFIED**. Locking the primary row does not bound all flush targets, deferred checks or table locks. Sample native UPDATE NOWAIT evidence does not generalize. |
| Queue WAITING / expired LEASED selectors | Existing ordered FOR UPDATE SKIP LOCKED paths unchanged | Existing default/Oracle selection and priority/recovery controls retained. Point policy is not a queue optimization or a bound on arbitrary locked prefixes/table locks. |

## Contention and accounting

LocalContention is a framework-neutral port failure. Opted-in adapters translate
only the observed Oracle code/state into a detached diagnostic cause, without the
entity-bearing provider exception or a connection/session chain. They recognize
Oracle54; parser errors, uniqueness errors, ORA-01013 and recoverable network errors
are not reclassified as that outcome. The attempt must exit/roll back before
`coordinator.failed`; failure persistence rejects an active caller transaction.
The separate retry outcome is `LOCAL_CONTENTION`, not FETCH_FAILED/FETCH_TIMEOUT.
It consumes the existing failed-work attempt budget, not an owner-failure metric.
Backoff uses existing retry-base/retry-max, exponential growth, 0.5-1 jitter and a
positive millisecond floor; max-attempts parks work DEAD. New explicit generations
retain existing reset/priority semantics; passive visits cannot reset this backoff
or revive DEAD. No in-place retry loop is added.

If the one retry-metadata transaction also refuses, the exception propagates with
the original failure suppressed, without recursive persistence retry. The committed
lease/token/generation remains recoverable after expiry. The attempt counter cannot
advance when that write cannot commit: max-attempts is not a global bound across
perpetual metadata-write failures. Recovery is lease-paced, requires monitoring,
may repeat an already performed owner read, and spends another admission. No permit
is refunded and no earlier owner call is erased. A failed enqueue is not accepted;
an ID returned inside a larger caller transaction is provisional until that commits.

## Accepted subset and remaining gates

The [accepted subset](LOCK-POLICY-DECISION.md) changes only the evidenced existing
work/JPA criterion. The plain-UPDATE reproducer and required full OracleIoIT gate
are unchanged and apply to unprotected reachable paths. New quota regression PASS
does not waive its registration or transport requirements. No complete bounded
persistence claim is made while legitimate first-work/projection/state/quota insert
proof, applicable waits, transport and uncertain COMMIT remain open. Full is NOT_RUN
until intended supported paths and gate mapping are ready. All parent gates stay open.
