# First-creation reachability under supported writers

Audit base: `b280af476cb76b3b147af2aa893dd44007819d6f`; accepted work/JPA
and quota existing-row policies are unchanged. See [writer model](SUPPORTED-WRITERS.md).

Actual pre-control source `f55f9299d0cdb67a742e8cf0cf8aa5f001f31333`, tree
`15e75f5f1292ed0850bfffc73486939c4fe0e02a`: default run
`35c35ba7-a594-45ee-8fed-fec7a02536a7` PASS/149 JUnit/79 Python/exit0;
focused run `1ea8bf28-27f0-4225-970e-6298b62e8335` PASS/6 evidence tests/exit0.
All four default work/quota cases observed B ACTIVE/row-lock WAITING, with its
exact INSERT SQL and A as BLOCKING_SESSION before A's planned commit/rollback.
Those are UNBOUNDED_DEFAULT_WAIT observations, not bounded qualification. The
two projection/state cases refused B at the work fence before JPA entry, verified
paired first-create commit/rollback, newer intent/local revisions and recovery.
No out-of-protocol starter-table writer or release-assisted cancellation was used.

This measurement justifies an explicit `JdbcInsertPolicy.ORACLE_26_NOWAIT` candidate
for single-row MS_WORK explicit/passive creation and MS_QUOTA registration only.
It validates Oracle26ai/JDBC23.26 metadata; DEFAULT and older configurations are
unchanged. It does not modify state/projection INSERT, existing locks or queue SQL.
The real candidate run remains separate from the pre-control results above.
Oracle documents [INSERT NOWAIT](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/INSERT.html)
as statement rollback on lock conflict; public transaction boundaries must still
roll back the whole failed attempt. No in-place transaction retry is added.

| First creation | Public entry and transaction | Existing locks and compliant competitor | Commit/acceptance boundary |
|---|---|---|---|
| MS_WORK | SyncGateway.request / ModelSync.request, SyncTriggers or RequestSync; passive requestRefresh also reaches enqueueRefresh. SpringLocalTransactions REQUIRED joins caller or creates transaction. | Missing-row SELECT FOR UPDATE locks no key. Two compliant producer instances can both observe absence and INSERT the same WorkKey.storageId. No projection lock is held yet. | Standalone request commits before returning. A ticket returned inside an outer transaction is provisional until that outer commit; rollback/failure is not accepted intent. |
| MS_QUOTA | Actual default JdbcQuotaGate.register, called by starter startup or explicitly by host. Independent REQUIRES_NEW transaction. | Consistent read of spacing reserves no missing key. Two compatible application instances registering the same new group can race. Existing-row acquire/cooldown policy cannot protect missing-row INSERT. | register returns successfully only after independent commit. An outer rollback cannot undo already committed registration; failed registration cannot be reported configured. |
| Projection | SyncCoordinator.finish -> SyncResultApplier -> JpaProjectionStore.compareAndSet; application business writer must enqueue/lock ledger first in the same transaction. | finish validates/locks current MS_WORK token before load, lookup, persist/flush. Compliant business creation must acquire the same work fence first. Other workers cannot claim the held lease/locked row; another fenced producer cannot reach projection INSERT while A holds it. | Projection and successful state/ledger completion commit together. Outer caller transaction still owns the final commit. JPA flush alone is not success. |
| MS_STATE | SyncCoordinator.finish -> SyncCompletion -> JdbcWorkStore.complete/completeOnDemand. Same local transaction as projection. | currentToken locks MS_WORK again before UPDATE-then-INSERT. Only the current fenced result writer may create success state. A second compliant worker cannot manufacture or share A's lease; an expired claim also observes queue locking/fencing. | Successful state and projection/ledger commit atomically. On rollback neither new projection nor state survives; lease/intents remain recoverable. |

Experiments must pause A after actual adapter insertion and before commit, inspect B
with A unresolved, then resolve A commit/rollback separately. A deliberate commit or
rollback that lets ordinary INSERT finish demonstrates serialization behavior, not
cancellation or a bounded conflict policy. First work/quota need measured conflict
handling if that default wait is observed. Candidate native INSERT NOWAIT must be
explicit, version-specific and limited to adapter-owned SQL; no JPA SQL rewriting.

Projection/state experiments use actual result application and ledger-first business
mutation, checking that the competing path stops before projection/state DML. They
do not introduce out-of-protocol starter-table writes or assert arbitrary mappings,
extra unique constraints, triggers or external SQL are supported.

The separate provider limitation is unchanged: a cooldown extension that failed to
commit is unpublished to other instances. First-insert success does not close that
provider-budget publication/recovery requirement or the original UPDATE cancellation
failure for applicable unprotected paths. Current full gate is not removed.
