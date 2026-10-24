# Opt-in Oracle point-lock coverage

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
| First MS_STATE reconciliation record | Ordinary UPDATE-then-INSERT under work-record fence | Normal completion and retained atomicity tests; a conflicting out-of-band first INSERT wait remains **UNQUALIFIED**. Other writers must not be assumed to obey the ledger fence. |
| Existing MS_STATE update | Ordinary UPDATE, serialized for compliant workers by MS_WORK lock | Fencing/atomicity retained; arbitrary noncompliant writers, triggers and extra locks are not covered. |
| Quota registration | Existing consistent read / ordinary INSERT and duplicate-key reread | Unchanged; concurrent first-registration insert bound **UNQUALIFIED**. |
| Quota acquire/cooldown update | Separate authoritative transaction with ordinary FOR UPDATE and UPDATE | Unchanged, outside ledger transaction; local contention cannot refund permits or shorten cooldown. Quota row-lock/transport containment remains **UNQUALIFIED** by this option. |
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

## Active gate and proposed contract

The original [failure contract](FAILURE-CONTRACT.md), plain-UPDATE reproducer and
required full gate are unchanged. [The path-scoped proposal](JDBC-CONTAINMENT-PROPOSAL.md)
identifies the exact changes requiring review. There is no complete bounded
persistence claim while reachable insert/quota/flush/transport paths remain open.
Full requalification requires the explicit reviewed gate decision; this task does
not treat implementation authorization as acceptance of new qualification semantics.
