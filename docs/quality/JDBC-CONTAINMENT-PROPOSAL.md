# Locked-DML containment proposal

**DRAFT — NOT APPROVED OR IMPLEMENTED.** The active
[failure contract](FAILURE-CONTRACT.md) and original `OracleIoIT` gate are unchanged.
This proposal does not authorize full qualification or claim production readiness.

## Measured basis and limits

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

Corrected source `6edcf844ff1d9bdae5876d918e22c31b9619a346` adds four default
classifier regressions and collects fresh state before classifying errors. It
also observes the exact Oracle SID/serial before blocker release, separating
client exit from a possible residual server lock wait. Default121 JUnit/64 Python
passed; this source's Oracle execution is BLOCKED by11.474–11.493 GiB free versus
the declared12 GiB scoped budget. No passing containment qualification exists yet.

## Proposed replacement for the statement/row-lock qualification row

Subject to explicit review **after the missing safety checks execute**, replace
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

Restore the scoped12 GiB headroom and execute the corrected diagnostic once to
complete fresh-state, server-wait and subsequent-work proof. Preserve the failing
first attempt. Review this proposed contract against those results; do not change
the original failing gate or run full merely because a client read timeout fired.

Oracle documents [cancellation's dependency on a server/network response](https://docs.oracle.com/en/database/oracle/oracle-database/19/jjdbc/JDBC-troubleshooting.html),
[error18730](https://docs.oracle.com/en/error-help/db/ora-18730/), and the
[JDBC connection API](https://docs.oracle.com/en/database/oracle/oracle-database/26/jajdb/oracle/jdbc/OracleConnection.html).
The exact driver/server/forwarding cause of the missing cancellation response
remains unknown. Direct reproduction shows that Spring/Hikari is not necessary
for the failure; it does not identify a specific Oracle or Docker defect.
