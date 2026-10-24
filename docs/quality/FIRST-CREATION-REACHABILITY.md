# First-creation reachability under supported writers

Audit base: `b280af476cb76b3b147af2aa893dd44007819d6f`; accepted work/JPA
and quota existing-row policies are unchanged. See [writer model](SUPPORTED-WRITERS.md).

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
