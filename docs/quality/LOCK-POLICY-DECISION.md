# Accepted existing-row criterion - 2026-10-24

The owner explicitly accepted the following subset of the proposal at report
`74916ce77bcaca655621c5dd02c06da8d0ac763e`, reviewed proposal Git-blob SHA-256
`7291ae37af1f0620d5f42651d60e9c5571ec1e89d4283d1d4f9557f065618231`.
Evidence remains bound to source `2eea1d849dfd8d5f7f0ee1ee6f3386412b282477`,
tree `8338eff0badecc4be2b8855f1e4e9fedbc8da0d6`, focused run
`d7f2d3fe-348c-41a5-89b3-0f7ed0785200` (21/21, not full qualification).

| Accepted requirement | Actual integration test in OracleLockPolicyIT |
|---|---|
| Opted-in existing MS_WORK point lock refuses with blocker held; failed enqueue is not accepted | enabledProducerRefusalIsNeverAccepted |
| Qualified JPA mapping lookup refusal, whole rollback and retained intent; no DML after refusal | resultRefusalRollsBackBeforeDurableRetryAndRespectsCooldown |
| Per-operation refresh while lookup's exclusive lock remains held | enabledSuccessRetainsRefreshGenerationFencingAndLocalRevision |
| Rollback before separate LOCAL_CONTENTION outcome, retained provider budget/cooldown | resultRefusalRollsBackBeforeDurableRetryAndRespectsCooldown |
| Single reported metadata refusal, durable fenced lease, later recovery | lockedRetryMetadataIsReportedOnceAndLeavesLeaseRecoverable |
| Bounded writable-metadata attempt/backoff policy, actual worker physical/pool cleanup | actualWorkerBoundsContentionRetriesWithoutForgettingOwnerReads |

For these operations, database NOWAIT refusal, physical return within the existing
5s observation budget, exact-session absence of residual lock wait with the blocker
held, verified rollback/fresh state, preserved intent and healthy/disposed resources
are the accepted criterion. Independent client cancellation is not the mechanism
required for this subset. All seven atomicity assertions were also retained in
DEFAULT and ORACLE_NOWAIT configurations. This closes that scoped integration
milestone, not a parent release gate.

The original plain-UPDATE cancellation failure and reproducer remain FAILED and
unchanged. They continue to apply to unprotected reachable persistence paths.
The existing full OracleIoIT gate is retained: no filtering, skip, reclassification
or blanket waiver is authorized. First-insert, quota, extra-flush, transport,
uncertain-COMMIT and other applicable release requirements remain open until
individually supported. A new quota option/test is not retrospective acceptance
of its results. Complete bounded persistence and production readiness are NO-GO.

See [coverage](LOCK-POLICY-COVERAGE.md), [failure contract](FAILURE-CONTRACT.md)
and [QM](../../QM.md). The reviewed proposal text and earlier evidence stay intact.
