# Database-enforced lock-wait prototype

## Executed evidence - 2026-10-24

Clean source `eb182094dc168573accb8c12f8e63264f771f192`, tree
`904b24d22194b2e4c303ce14538e8368f711b3ec`, passed matching default run
`ff2ad62c-9be7-40b2-bb7a-927db14c5036` (125 JUnit/67 Python, exit0).
After bounded post-build12 GiB admission passed, selected diagnostic run
`c82fa300-a9e4-4a6e-ab9d-274d4f824936` passed all seven cases, wrapper/launcher0.
The earlier a7 diagnostic exit1 is preserved: a queue-control claim was not
disposed, retaining its provider reservation; four cases failed before their
operation. Only test cleanup/evidence changed. Production adapters/defaults and
the original failing gate are unchanged.

Five refusal sequences/15 attempts produced real Oracle54 while their original
blocker remained held. Client refusal4-17ms, outer transaction return7-66ms;
these include setup/observation work. Exact SID220/serial8623 was INACTIVE with
LOCK_WAIT0/TX_ACTIVE1 before rollback, then LOCK_WAIT0/TX_ACTIVE0. Earlier guard
writes/locks rolled back, durable intent persisted, pools remained healthy and
no physical operation remained. Blocker release was cleanup only, not mitigation.
Queue and generation/fencing/revision controls passed separately.

Measured existing-row support covers exact JDBC point NOWAIT plus this mapping's
JPA lookup and session-configured real refresh. Separately, Oracle26ai23.26.3 /
Hibernate7.4.5 native UPDATE/INSERT NOWAIT worked for the sample's nonbatched
single-table flush-extra-row and unique-insert fixtures. This is not qualification
of arbitrary generated SQL or other Oracle versions. Smallest justified change
for review: an explicit Oracle point-lock option and explicit lookup/refresh lock
properties, with durable bounded contention handling. No production SQL inspector
or adapter/configuration change is shipped by this diagnostic.

[QM](../../QM.md) / [raw evidence](../../validation/qualification/2026-10-24-nowait-execution/README.md)
report each case, complete SQL, retries, limits, first failure and cleanup.
New ledger/state/quota insert conflicts, additional tables, batching/RETURNING,
FK/deferred constraints, cascades/triggers, custom SQL, table locks, network/COMMIT
uncertainty and universal server cleanup remain unqualified. The earlier direct
UPDATE cancellation failure is not repaired; no replacement contract is approved.
Full/A-B-C/large seeds NOT_RUN. The original audit/prototype design follows.

## Audit before execution

Baseline `43040e73258ecab1ebd761511523c744a0656bb7`; known-ID owner-to-Oracle/JPA
scope only. The accepted `2c39b75` cancellation diagnostic and its report/archive
remain unchanged. This prototype does not repair that direct UPDATE diagnostic
or approve a replacement contract. No production implementation/default is changed.

| Actual path | Current SQL/control | Missing bound / prototype |
|---|---|---|
| `JdbcWorkStore.locked`, used by enqueue, passive enqueue, lockValid, currentToken/completion/reschedule | `select * from MS_WORK where work_id=? for update` | No explicit database wait bound. Test-only `NowaitPointJdbcTemplate` rewrites only this exact query to `for update nowait`, through the real store. |
| Queue WAITING/recovery selection | Ordered index paths with `for update skip locked` | Separate queue semantics, unchanged. A small locked-row control verifies another item can be claimed. This is not table-lock or arbitrary-prefix qualification. |
| `JpaProjectionStore.load` | Mapping lookup with `LockModeType.NONE` | Detached immutable snapshot after transaction. Custom lookup queries/auto-flush can have additional effects; no implicit deadline. |
| `compareAndSet` mapping lookup | `find(..., PESSIMISTIC_WRITE)`; sample mapping calls `em.find` without lock properties | Lock mode alone does not establish NOWAIT. Prototype mapping supplies timeout0; generated SQL and Oracle refusal must prove it. |
| `compareAndSet` refresh | `em.refresh(entity,PESSIMISTIC_WRITE)` with no explicit properties | Prototype explicitly sets timeout0 on the fixture's transaction-bound EntityManager. A deliberately deferred lookup isolates this actual refresh call. No inference from hints alone. |
| Existing-row flush | Sample entity field mutation then `em.flush()`; ordinary versioned UPDATE | Proper earlier lock protects this single row, not all flush targets. Test an extra row dirtied by the fixture mapping; an explicitly enabled inspector appends native DML NOWAIT only to synthetic SAMPLE_CUSTOMER UPDATE/INSERT. |
| Missing projection/create | `em.persist` then flush; tenant/subject unique constraint | No row exists to lock. Test a real competing uncommitted insert with native INSERT NOWAIT. Native syntax support on the pinned image must be measured. |
| New ledger/state insert, quota SQL, custom mapped tables/triggers/cascades | Ordinary INSERT/UPDATE and application/provider-generated SQL | Not covered by the point-lock prototype. In particular, an empty SELECT NOWAIT does not lock a nonexistent key or bound a unique/FK conflict. Remain unqualified. |

The existing recovery fixture's explicit lookup WAIT1 does not prove a bound on
the store's separate refresh or arbitrary flush. Standard exception/transaction
timeouts and connection read timeouts remain separate from database lock refusal.
`DDL_LOCK_TIMEOUT` is not used as a DML timeout. No privileged session kill,
application-owned datasource change or global setting change is proposed.

## Executable experiment and acceptance

`python scripts/full-oracle-container.py --nowait-diagnostic` selects only
`OracleNowaitDiagnostic`, never the old A/B/C matrix, a million-row fixture,
PIT/Gatling or full verification. It requires a current successful default build
and the existing acknowledged, pinned disposable container route. Runtime3 /
observer2 connections; JDBC query2s/read10s as layered cleanup controls, observation5s;
single test-owned execution lane, three attempts with each original blocker held.
JVM heap512 MiB/direct128 MiB, Oracle6 GiB/4 CPU, scoped planning12 GiB/full18 GiB
unchanged. Production adapters, interfaces and active gate remain unchanged.

For each refusal: require Oracle54 (not arbitrary errors), SQL containing NOWAIT,
client execution exit, exact SID/serial snapshot before rollback and afterward,
no remaining row-lock wait, transaction rollback receipt, release/rollback of an
earlier guard-ledger lock/write, unchanged lease token/generation and absent success
state, healthy connection/pool and no accumulation. Lookup/refresh refusal must
submit no projection DML. Flush refusal must terminate the flush without later DML
and roll back any preceding attempt work. Persisted intent recovery/old-token
rejection are inspected separately after fixture cleanup; cleanup earns no
mitigation credit. Unknown connection outcomes stop further probes rather than
allowing unlimited replacement attempts or asserting successful drain.

The real starter `JpaTransactionManager`, JDBC ledger and `JpaProjectionStore`
are used. Ledger-before-projection ordering, local revisions, generation handling
and token fencing are retained. Synthetic owner reads occur outside transactions;
no owner I/O occurs while attempt locks are held.

## Limits and possible implementation changes (not approved)

The explicit test seams are prototypes, not consumer SQL rewriting. A production
candidate needs an Oracle-aware, reviewed point-lock policy in the JDBC adapter,
and a mapping/refresh contract carrying explicit lock properties. It must classify
database lock refusal and bound retries without shortening provider cooldown or
losing durable intent. Current failure policy treats these exceptions as counted
FETCH_FAILED; if the ledger itself is contended, retry-metadata persistence can
also refuse and the durable lease remains for recovery. Measure this, do not hide it.

Native DML NOWAIT may offer a narrower Oracle-version-specific extension for flush
and insert, but provider-generated SQL, batching, RETURNING, additional mapped tables,
unique/FK constraints, deferred checks, cascades and triggers each need explicit
coverage. This fixture uses one sample table with batching disabled, plus an extra
row to expose an unprotected flush target. It does not qualify arbitrary mappings,
older Oracle versions, table/DDL locks, network loss or uncertain COMMIT outcomes.

Oracle documents [default SELECT lock waits](https://docs.oracle.com/en/database/oracle/oracle-database/26/adfns/sql-processing-for-application-developers.html)
and [new WAIT/NOWAIT DML support](https://docs.oracle.com/en/database/oracle/oracle-database/26/nfcoa/oltp_manageability_perf.html).
These references motivate experiments; actual pinned-image SQL/results must decide
the supported scope. The active cancellation gate and production NO-GO stay intact.
