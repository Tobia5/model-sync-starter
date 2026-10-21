# Production-readiness assessment

## Review-branch evidence and first supported scope

The historical main assessment below is preserved. It predates the successful review-branch qualification of source `4f440807f20c568a2240ed5ce32f1aab8e9558b0`, full run `3177a6c3-3b64-4e5b-b40b-dc18bf1c7884`. Its statement that no full evidence existed is historical, not the current review-branch result. See the [immutable baseline report](https://github.com/Tobia5/model-sync-starter/blob/a67966d16ccf6c140f4c95bebc2ddfd3ad497364/QM.md) and the [current QM report](../QM.md).

The first supported scope is **known-ID owner-to-Oracle/JPA reconciliation**, using one correctly enlisted local datasource and `JpaTransactionManager`, application-supplied detached DTO mappings and an application-authorized owner reader. This scope does not promise inventory/discovery, broker delivery, vendor connectors or remote writeback. No such feature is added by qualification work.

The configured baseline suite passed. A separate qualification of source `1a9187ce0886912c14a2f77dbcccd3287a37e4a8`, tree `0276124dd146d4fc9ae89dfe5ebddf7154840374`, full run `8a2204e2-1371-4cce-8b7a-7ee5c573ae14` also passed: 63 Surefire / 13 Oracle tests, including seven real starter/JPA/JDBC atomicity cases. It adds actual claim plans, separate counters/waits, eight 100k distributions and an unchanged million-row sample. The sample completed 211 reconciliations (201 during measurement), not one million. Both qualifications and intermediate failures retain their own source-bound artifacts; the earlier baseline is unchanged.

Production remains **NO-GO** while applicable [parent release gates](../TODO.md) remain incomplete. The tested single-datasource rollback gap is closed for the injected cases, including physical Oracle commit rejection, old leases and local revisions. Independent-process/crash/racing-insert proof, production-like capacity/SLOs, real provider budgets, timeouts, operations/security, consumer/compatibility/supply-chain and retention gates remain open. Claim selection still dominates measured logical work; no production SQL/index optimization or production-readiness claim follows from the configured full PASS.

## Historical main assessment

**Date:** 2026-10-20. **Remote baseline:** `a555ffaf8681b6dcc2597f16a84bf5b2a45c11d6`.
**Decision:** NO-GO for a production-qualified release or an unattended million-record Oracle deployment until the applicable P0 gates in [TODO](../TODO.md) have evidence.

## Rating and interpretation

| Dimension | Engineering rating | Reason |
|---|---:|---|
| Architecture | 7/10 | Explicit bindings, authority boundaries, dependency-free core, durable work, version checks and lease fencing form a useful foundation. Operational proof and complete integrations lag behind the design. |
| Usefulness for this rate-limited, multi-model Oracle/MDM context | 8/10 | Reusable ownership/freshness/identity-aware decisions can replace repeated application-specific reconciliation logic. Benefit depends on real adapter reuse. |
| Current verified production readiness | 4/10 | Offline contract evidence exists, but no actual full Maven/Oracle/scale pipeline evidence was available and the requested extension is not on main. |
| Benefit for a simple single-source CRUD application | 4/10 | Ordinary cache and scheduling facilities are usually sufficient; this runtime introduces extra schema, operational and upgrade responsibilities. |

These are review judgments, not percentages complete or certification scores. A hard release blocker remains a blocker regardless of the average score. More pages of documentation do not raise the runtime qualification score.

## Evidence boundary

The connected GitHub API was queried during this review: main remains at the baseline above, Actions listed zero workflow runs, and no open issues were returned. The current parent POM targets Java21 / Spring Boot4.1.1 and has no publication profile. The current Oracle test is a single opt-in claim/completion smoke test, not a concurrent or migration suite. Main has not incorporated the separately delivered job/event/visit extension.

Current source was read for the root/sample POM, OracleSmokeIT, JdbcQuotaGate and ReactorSyncWorker; earlier pinned reads cover SyncCoordinator, ModelSync, ModelBinding and JdbcWorkStore. This is a scoped engineering review, not an exhaustive security audit. The offline workspace is reconstructed from the supplied archive plus verified touched-file baselines; it is not a complete fresh clone of current main. The cumulative patch only changes reviewed baseline files and adds new files, preserving unrelated source formatting.

The produced update includes the previous trigger/arc42 change plus this qualification backlog, contributor rules, guarded development publishing and CI changes. It is not remotely merged. GitHub denied both branch creation and issue creation with HTTP403. No code/issue was committed remotely, no package was published and no Codex worker was launched by this review.

## Main findings

1. **Proof gap:** dependency-free tests cannot establish Spring/Hibernate API compatibility, transaction integration or actual Oracle behavior. The complete Maven build must be the first cloud-worker task.
2. **Oracle gap:** the smoke test validates one basic path. Production needs concurrent claim/coalescing, rollback, crash/fencing, migration and shared-quota tests on the target database/driver.
3. **Scale risk:** quota denial happens after a work item is claimed and inspected. With millions due and many workers, database work can grow much faster than admitted HTTP work. This is a risk to benchmark, not a demonstrated throughput failure. Retain bounded workers; measure before changing admission design.
4. **Isolation risk:** the runtime has separate persistence and blocking-owner schedulers, but does not establish per-provider bulkheads or a hard urgent reservation across pods. A slow provider and delayed cancellation need explicit tests.
5. **Completeness gap:** known-subject scheduling is not an external inventory. The BoundaryInventory/IdentityResolver/dataset contracts are useful interfaces, not a finished MDM/Vista/DWH implementation.
6. **Event semantics:** a synchronous enqueue and durable work ledger preserve intent when correctly enlisted. They do not automatically provide broker dedupe or exactly-once delivery. A forced change event also does not by itself make previously fresh reads linearizable.
7. **Operational gap:** latest state and class-name logging are insufficient to operate a large reconciliation fleet. Metrics, bounded query/lock waits, health and authorized replay must be proven.
8. **Packaging gap:** before this patch there is no configured remote Maven distribution. A starter JAR alone is insufficient; consumers need its parent and transitive module POMs/JARs. Publication success and consumer usability still have to be executed.

## Important strengths to preserve

- Do not infer data ownership from arbitrary JPA entity fields; use explicit bindings and mapped source-owned fields.
- Keep owner HTTP outside local DB transactions, and complete under lease/version checks.
- Keep freshness validation, successful agreement, retention and source-change time separate.
- Do not delete locally on an ambiguous owner absence or serve unverified data as fresh.
- Keep passive visits separate from privileged explicit/event requests. Visits must respect retry backoff, provider cooldown and DEAD quarantine.
- Keep WebFlux blocking bridges honest: JPA is still blocking even when the orchestration API returns Mono.

## Qualification stages

**Current:** reviewable reference implementation with offline contract tests.

**Pilot candidate:** full clean CI, real Oracle atomicity/concurrency and migration tests, one supported owner adapter, authorization/timeout policy, basic observability and isolated staging failure tests. An on-demand known-ID pilot can explicitly exclude complete-population discovery and broker/Vista delivery.

**Production candidate:** target-volume multi-pod/owner-failure evidence, documented SLOs and recovery, security/replay/retention policies, package consumer tests and all gates required by the promised scope.

**Reusable stable release:** maintained compatibility matrix, reproducible release evidence, dependency/SBOM review, verified distribution and a documented upgrade path for external consumers.

## Capacity sanity check

For N subjects, average c physical requests per refresh, rate R requests/minute, usable budget fraction u, and 24/7 execution:

`minimum refresh days = N * c / (R * 60 * 24 * u)`

Example assumptions only: 5,000,000 subjects, one call each, 100 requests/minute, u=1 imply about 34.7 days before retries or competing usage. At u=0.7 the lower bound is about 49.6 days. More pods or WebFlux cannot overcome that API budget. Delta/inventory/bulk capabilities or a looser freshness SLO are necessary where the budget does not fit.

## Evidence needed to revise the rating

Record the commit SHA, environment versions, exact commands, test counts/skips, Oracle DDL version, workload distribution, measured request budget and outcomes. Store actual logs/CI run references. Do not mark a gate passed from a generated test file, a scheduled workflow or an unexecuted Codex instruction.

See [packaging](PACKAGING.md), [Spring comparison](SPRING-COMPARISON.md), [production runbook](PRODUCTION.md) and [validation evidence](../validation/readiness/summary.json).
