# Path-scoped Oracle lock-policy proposal

**REVIEW REQUIRED - NOT A CHANGE TO THE ACTIVE QUALIFICATION CONTRACT.**
Implementation authorization does not approve replacement of the original failing
statement-cancellation gate. The plain-UPDATE reproducer, OracleIoIT requirement,
FAILURE-CONTRACT.md and all source-bound historical evidence remain unchanged.

## Current candidate

Final implementation source `2eea1d849dfd8d5f7f0ee1ee6f3386412b282477`, tree
`8338eff0badecc4be2b8855f1e4e9fedbc8da0d6`: default
`fb25cfb1-075e-407a-8ca2-8bfb276278b3` PASS/141 JUnit/72 Python/exit0;
focused Oracle `d7f2d3fe-348c-41a5-89b3-0f7ed0785200` PASS/21 of21/exit0.
It includes actual starter/worker cases plus all seven original atomicity assertions
in both policy modes. JDBC refusal12ms, JPA lookup7ms, contended result point5ms;
outer transaction return85/13/10ms. Metadata refusal3ms retained recoverable lease
and original detached failure. Exact sessions had no residual row-lock wait with
blockers held and no active transaction after rollback. The worker's3 successful
synthetic reads produced0 completed syncs and bounded DEAD/LOCAL_CONTENTION.
Refresh retained the lookup's lock, independently verified before flush; Hibernate
elided a redundant locking SELECT. [QM](../../QM.md) and
[immutable attempts](../../validation/qualification/2026-10-24-lock-policy/README.md)
retain exact SQL, first failures, earlier pass, source analysis and cleanup.
**This is measured candidate evidence, not acceptance of the proposal below.**

The adapters now offer explicit opt-in Oracle existing-row point NOWAIT locking,
JPA mapping lookup options and per-operation refresh options. Defaults remain
compatible. There is no production SQL-rewriting inspector or native INSERT/UPDATE
NOWAIT extension. Opted-in custom mappings must explicitly implement the capability;
no legacy fallback is permitted. See the [coverage matrix](LOCK-POLICY-COVERAGE.md).

The accepted eb18209 diagnostic/report baba54c motivates this implementation but
is not evidence that the new production path passed. New default and focused
Oracle runs must be recorded with their own source/tree/run in [QM](../../QM.md).
The new scoped command selects production integration and retained atomicity only;
it does not rerun the prototype, cancellation A/B/C, scale or full verification.

## Exact proposed requirement changes (pending explicit acceptance)

| Active requirement | Proposed path-scoped interpretation | Status |
|---|---|---|
| FAILURE-CONTRACT statement/row-lock row: effective statement2s, observed5s, plus original OracleIoIT ORA-01013 assertion | For opted-in, verified existing-row JDBC/JPA lookup/refresh only: require database NOWAIT refusal54, physical client return within5s, exact-session no residual lock wait with blocker held, whole local rollback, fresh committed-state inspection and healthy/disposed connection. Explicitly distinguish statement refusal from whole-transaction rollback. | Proposed only; original row/test remain active and failing. |
| Independent plain UPDATE cancellation diagnostic | Retain its FAILED result and reproducer. NOWAIT point SELECT is a different operation, not a passing rerun or a repaired cancellation mechanism. | No removal/relabel authorized. |
| Pool/acquisition, connect/read, transaction, owner and shutdown requirements | Retain separate host-configured safeguards and physical-completion proof. Network timeout is client containment, not proof of server termination or successful drain. No global datasource change. | Unchanged. |
| Local retry outcome/accounting | Require rollback before separate LOCAL_CONTENTION persistence, finite attempt/backoff policy when metadata is writable, one reported failed metadata write otherwise, durable fenced intent and retained owner admission. | New integration assertion; not a waiver of other bounds. |
| Complete bounded persistence | No completeness claim while reachable first-work/projection/state inserts, quota registration/row updates, extra flush targets, custom SQL and uncertain transport/COMMIT paths lack qualified controls. Known external IDs do not remove those local races. | Remains unqualified/release-blocking. |

A reviewed decision must identify the exact supported deployment/path scope and
which existing gate, if any, changes. Passing the new integration suite alone
cannot approve this table, remove the original full requirement or close parent
release gates. A scoped criterion may be accepted while complete persistence
containment remains blocked; those statuses must stay separate.

## Retry semantics and remaining server-lifecycle gaps

Oracle54 becomes an explicit LocalContention port failure only for opted-in
adapters. It is not an owner/network failure. Existing exponential bounded backoff
and max-attempts apply to writable LOCAL_CONTENTION retry outcomes; no immediate
retry loop, permit refund, provider cooldown change or owner I/O under locks.
If metadata persistence is also locked, it is attempted once and propagated with
the original error retained. Durable lease recovery may repeat an owner read and
spend a new admission. An unwritable retry row cannot advance the counter; this
is not a finite global-attempt guarantee under permanent metadata-write failure.
Failed enqueue transactions cannot be acknowledged as accepted.

The policy does not bound ordinary INSERT/UPDATE/flush SQL, additional mappings,
FK/deferred checks/cascades/triggers, quota row locks, table locks, network loss
or uncertain COMMIT. Layered transport deadlines still need deployment-specific
qualification; known network containment can leave server work unresolved. No
DDL_LOCK_TIMEOUT, privileged session kill, automatic replacement storm or general
server-termination claim is introduced.

---

## Historical proposals and measurements (preserved)

The following network-only draft and prototype notes are retained as history.
They are superseded as the current proposal by the path-scoped text above, without
changing the accepted measurements or approving a new contract.

# Locked-DML containment proposal

**DRAFT — NOT APPROVED OR IMPLEMENTED.** The active
[failure contract](FAILURE-CONTRACT.md) and original `OracleIoIT` gate are unchanged.
This proposal does not authorize full qualification or claim production readiness.

## Measured NOWAIT candidate - 2026-10-24

Source `eb182094dc168573accb8c12f8e63264f771f192`, tree
`904b24d22194b2e4c303ce14538e8368f711b3ec`, default
`ff2ad62c-9be7-40b2-bb7a-927db14c5036` PASS/125 JUnit/67 Python/exit0;
diagnostic `c82fa300-a9e4-4a6e-ab9d-274d4f824936` PASS/7 cases/exit0 after
post-build12 GiB resource admission. The first a7 attempt (exit1,3/7) remains
failed: an undisposed queue-control claim blocked four later setups. The fix is
test-only and asserts a follow-up claim; production and active gates are unchanged.

Existing-row JDBC point locks and pinned Hibernate lookup/real refresh generated
NOWAIT SQL. Separate single-table extra-row UPDATE and racing INSERT native NOWAIT
also refused54 on Oracle26. Across15 attempts with original blockers held, exact
SID/serial snapshots showed no remaining lock wait before rollback, and no active
transaction after rollback; earlier guard writes/locks were released. Refusal4-17ms,
outer transaction return7-66ms, healthy pool/reuse and zero physical remainder.
Release happened only after proof. This demonstrates database refusal without
releasing the blocker for these statements, not independent cancellation of the
original plain UPDATE or general server cleanup.

The smallest supported candidate for review is explicit Oracle point-lock NOWAIT
plus mapping/refresh lock properties; a production change still needs the approved
contract, refusal classification and durable bounded retry behavior. Retry metadata
can itself refuse under a ledger lock. Native DML is a separate version-specific
extension; arbitrary mapping/SQL, additional tables, new ledger/state/quota insert
races, FK/deferred constraints, cascades/triggers, older Oracle, transport failures
and uncertain COMMIT remain unqualified. No generic SQL rewriting policy is approved.

See [measured audit](NOWAIT-PERSISTENCE-AUDIT.md), [QM](../../QM.md) and
[immutable raw attempts](../../validation/qualification/2026-10-24-nowait-execution/README.md).
The active original gate remains FAILED/open; full was not run and production is
NO-GO. This updates evidence within the existing proposal, not its approval status.
The following unexecuted-prototype section is historical; its blocked attempt and
all accepted2c39b75/43040e7 evidence remain unchanged.

## New database-enforced prototype — 2026-10-24

[The persistence-path audit](NOWAIT-PERSISTENCE-AUDIT.md) identifies unbounded
JDBC point locks and separate JPA lookup/refresh/flush controls. A test-only
prototype now exercises the real stores through explicit NOWAIT SQL and the pinned
Hibernate/JPA path. No production adapter, interface, datasource/global default or
active gate changed. Queue SKIP LOCKED remains separate.

Clean source `ab7ae1d0bfb15670b6de3dc8205573c9fc04844c`, tree
`4601bfdfab27e849bd15ce96647b4689896009f0`, default run
`dc582cf4-df8a-4b56-b198-4a7d03f094e8`: exit0,125 JUnit/27 suites,67 Python,
zero failures/errors/skips. Its new Oracle tests **did not run**: host memory
11.402–11.430 GiB versus the retained12 GiB scoped budget blocked provisioning.
There is no new generated-SQL, database-refusal, server-exit, rollback or retry
measurement, and no measured candidate decision yet. The exact missing execution
prerequisite is host headroom, not external Oracle credentials or image access.

The next diagnostic is `--nowait-diagnostic`, not the unchanged A/B/C matrix or
full suite. It tests three fixed attempts under each original blocker, separately
records database refusal and whole-transaction rollback, and stops on an uncertain
physical operation. Native DML NOWAIT support must be checked on the actual pinned
image before attributing any flush/unique-insert containment. New ledger/state
insert races, additional mapped tables, triggers, deferred constraints and custom
adapter SQL remain outside any existing-row SELECT NOWAIT conclusion.

The accepted `2c39b75` findings and complete `43040e7` report/evidence are preserved
unchanged below/in Git and their existing archives. This unexecuted prototype does
not resolve their server-lifecycle gap or approve replacement of the original gate.

## Current measured basis and limits

Corrected diagnostic source `2c39b759cd9295c35eeddf6587094816950ccdf3`, tree
`03f1aa3e069863207151b28f2389f899f089e7fb`, run
`31f8e5ee-aa5f-4ec3-920d-4b952f90c089`, completed six evidence checks successfully.
Default121 JUnit/64 Python also passed at this exact source. Query2s/read10s/
observation5s and the pinned platform were retained; C alone set network3s.

| Case | Direct JDBC client exit | Spring/Hikari client exit |
|---|---|---|
| A automatic |10007ms, cancellation FAILED |10013ms, cancellation FAILED |
| B explicit cancel once |10017ms;cancel returned0ms |10008ms;cancel returned0ms |
| C network3s |3007ms, client I/O contained |3016ms, client I/O contained |

Every connection was disposed after recoverable18730/socket-timeout. **Oracle
remained ACTIVE/WAITING on the exact session's row lock after client exit in all
six cases.** After the fixture released the blocker, the lock wait was no longer
observed within4–9ms; that is release-assisted cleanup, not independent server
termination. Fresh committed state stayed `before` and subsequent healthy-connection
updates committed in6/6. All owned executors/pools closed, the table was removed,
and both current and first-attempt containers/volumes were independently absent.

Thus network timeout supplies **measured client containment and verified recovery
after cleanup**, with no independent server-cancellation bound. It is not a complete
replacement for physical database termination. The existing contract is still
FAILED. No full qualification was run. A changed contract requires explicit review;
the unresolved server lifecycle must not disappear from qualification or DRAINED
claims. [QM](../../QM.md) retains exact commands, exits and raw artifacts.

## Earlier attempt retained

Source `9b2476653a1f1364ea35d52f916e706910428ff3`, diagnostic run
`8bf36376-0d7b-45ef-9c12-c503aacdea5f`, used one locked synthetic row per probe,
query timeout2s, read timeout10s and observation5s. Direct JDBC and Spring/Hikari
both remained blocked beyond5s. Manual cancel returned in0ms at millisecond
resolution, but physical client execution exited only at10015/10017ms. Automatic
execution exited at10007/10003ms. The same DML with a separately configured
`Connection.setNetworkTimeout`3s exited at3002/3009ms. Every call ended with
`SQLRecoverableException`18730 / `SocketTimeoutException`; physical connections
closed. This is observed client I/O containment, not successful statement cancel.

All six diagnostic tests FAILED: the first classifier expected17002 and rejected
the pinned driver's18730 before fresh-state and subsequent-work assertions.
Those outcomes and server-side execution exit are **not verified**. Pools/threads
ended, the table was removed, and the owned container/volume were independently
confirmed absent. No competing-lock release is credited as cancellation success.

Intermediate source `6edcf844ff1d9bdae5876d918e22c31b9619a346` adds four default
classifier regressions and collects fresh state before classifying errors. It
also observes the exact Oracle SID/serial before blocker release, separating
client exit from a possible residual server lock wait. Default121 JUnit/64 Python
passed; this source's Oracle execution was BLOCKED by11.474–11.493 GiB free versus
the declared12 GiB scoped budget. The later source/run above completed the corrected
measurements after capacity recovered; these earlier receipts remain unchanged.

## Proposed replacement for the statement/row-lock qualification row

Subject to explicit review of the measured client/server distinction, replace
the requirement for independent ORA-01013 cancellation in this locked-DML case with:

1. Configure the fixture-owned connection with query timeout2s and a separately
   verified JDBC network timeout3s. Query cancellation remains best effort. The
   physical JDBC execute call must exit within5s while the conflicting lock is
   still held. A completed cancel invocation or cancelled Future is insufficient.
2. Accept ORA-01013 as statement cancellation. Separately recognize recoverable
   Oracle17002/18730 **only with a `SocketTimeoutException` cause** as connection
   failure. Such a failure is never labelled independent cancellation and requires
   disposal of the physical connection, not its return for reuse.
3. Inspect committed projection/ledger outcomes through fresh transactions.
   Verify no partial successful commit, successful subsequent work on a healthy
   connection, zero owned pool/executor residue, and no stale application. A
   failed rollback on the closed connection is not proof of rollback. Uncertain
   COMMIT remains subject to the existing fresh-inspection/recovery contract.
4. Observe the exact Oracle session/request after client exit and before releasing
   the blocker. Report any remaining server wait separately. If server work remains,
   report unresolved physical work; do not report DRAINED or server cancellation.
   A release-assisted server exit belongs to cleanup evidence and cannot pass a
   cancellation bound. Server cleanup/recovery needs its own demonstrated bound.

This draft changes the meaning of the gate: it would qualify layered client
containment and verified recovery rather than independent statement cancellation.
It is scoped to this locked-DML experiment, not arbitrary SQL, streaming responses,
network partitions or deployment configurations. The starter must not silently
reconfigure application-owned datasources. All atomicity, fencing, quota, shutdown,
architecture, mutation and load requirements stay in force. Production remains
NO-GO while applicable parent gates are open.

## What must happen next

Review whether the explicit client-containment contract is acceptable, including
the residual server wait. An independent server cleanup/recovery bound remains
unqualified; lock release is not its solution. The first failure and intermediate
resource blocks are preserved. Do not change the original failing gate or run full
until the original issue is resolved or a replacement contract is explicitly
approved and implemented. This document does not approve that change.

Oracle documents [cancellation's dependency on a server/network response](https://docs.oracle.com/en/database/oracle/oracle-database/19/jjdbc/JDBC-troubleshooting.html),
[error18730](https://docs.oracle.com/en/error-help/db/ora-18730/), and the
[JDBC connection API](https://docs.oracle.com/en/database/oracle/oracle-database/26/jajdb/oracle/jdbc/OracleConnection.html).
The exact driver/server/forwarding cause of the missing cancellation response
remains unknown. Direct reproduction shows that Spring/Hikari is not necessary
for the failure; it does not identify a specific Oracle or Docker defect.
