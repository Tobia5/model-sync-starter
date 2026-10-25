## Fresh execution attempt - 2026-10-25

Clean source09f371c/tree43135c7 passed a fresh default run4c0e79ad with156 JUnit/
84 Python checks, build/qualification/collection0. Post-collection admission
failed at11.115/11.224/11.046GiB against unchanged18GiB. Integrated full, all77
Oracle methods/runtime receipts, PIT/Gatling and current characterization NOT_RUN.
[QM](../QM.md) and [raw evidence](../validation/qualification/2026-10-25-integrated-named-target/README.md)
preserve exact identities and exits. No implementation/applicability change.
Provider cooldown publication/recovery, security/operations and other release
gates remain open. Production **NO-GO**; earlier reports below remain history.

## Current named-target evidence - 2026-10-24

The owner approved the exact [qualification mapping](quality/QUALIFICATION-TARGET.md)
after accepting sourcecfcc53d/report5819b80. Clean sourcef9f6551/tree2fa8194 passed
default156 JUnit/84 Python/exit0. Configured full admission is **BLOCKED**:
13.139-13.174GiB host available versus unchanged18GiB; wrapper/Oracle/PIT/Gatling
NOT_RUN. [QM](../QM.md) retains all original exits and raw evidence. Historical
plain-UPDATE cancellation stays FAILED; current characterization NOT_RUN. DEFAULT
is not bounded qualification. Failed cooldown publication/recovery and other
parent gates remain open; production **NO-GO**. Earlier assessments below are
preserved history, not a new execution result.

# Production-readiness assessment

## Review-branch evidence and first supported scope

First-creation update: clean source cfcc53d/tree1a900bb passed default155 JUnit/79
Python and12 focused Oracle cases. Explicit Oracle26 INSERT capability protects
first explicit/passive work and quota registration; actual work fence serializes
first projection/state creation. Four DEFAULT waits remain unbounded observations.
[Matrix](quality/LOCK-POLICY-COVERAGE.md) and [QM](../QM.md) record exact source/runs,
SQL, outcomes and cleanup. The original UPDATE failure/full gate and unpublished
failed-cooldown extension remain open. Full NOT_RUN; production **NO-GO**. This
does not change default Oracle support or promise arbitrary mappings/triggers.

Current scoped update: the owner accepted only the existing-work/JPA criterion
mapped in [LOCK-POLICY-DECISION](quality/LOCK-POLICY-DECISION.md) to source2eea1d8.
Source4057535/tree4bfbfae adds independently opted-in existing-quota NOWAIT and
passed default149 JUnit/76 Python plus six focused Oracle tests/eight refusals.
Three synthetic reads produced two completed reconciliations, not a capacity run.
The [writer model](quality/SUPPORTED-WRITERS.md) includes concurrent compliant
instances and retains legitimate first-insert gates. New full is NOT_RUN while
applicable requirements remain open. The original UPDATE cancellation failure,
all prior reports and full gates are preserved; production remains **NO-GO**.

The historical main assessment below is preserved. It predates the successful review-branch qualification of source `4f440807f20c568a2240ed5ce32f1aab8e9558b0`, full run `3177a6c3-3b64-4e5b-b40b-dc18bf1c7884`. Its statement that no full evidence existed is historical, not the current review-branch result. See the [immutable baseline report](https://github.com/Tobia5/model-sync-starter/blob/a67966d16ccf6c140f4c95bebc2ddfd3ad497364/QM.md) and the [current QM report](../QM.md).

The first supported scope is **known-ID owner-to-Oracle/JPA reconciliation**, using one correctly enlisted local datasource and `JpaTransactionManager`, application-supplied detached DTO mappings and an application-authorized owner reader. This scope does not promise inventory/discovery, broker delivery, vendor connectors or remote writeback. No such feature is added by qualification work.

The configured baseline suite passed. A separate qualification of source `1a9187ce0886912c14a2f77dbcccd3287a37e4a8`, tree `0276124dd146d4fc9ae89dfe5ebddf7154840374`, full run `8a2204e2-1371-4cce-8b7a-7ee5c573ae14` also passed: 63 Surefire / 13 Oracle tests, including seven real starter/JPA/JDBC atomicity cases. It adds actual claim plans, separate counters/waits, eight 100k distributions and an unchanged million-row sample. The sample completed 211 reconciliations (201 during measurement), not one million. Both qualifications and intermediate failures retain their own source-bound artifacts; the earlier baseline is unchanged.

Current Q3 qualification: source `ede199a33ad3a0ec5c911110bb84fd7407af1c25`, tree `7c9577e9de49a2f96b1093a65673250a46163da6`, full run `2b75d9cc-8836-4e99-b227-9d836e7e207a` passed: 105 Surefire / 26 Oracle tests, including all seven retained atomicity cases. Default local/CI passed 103 JUnit tests. Separate indexed WAITING/recovery paths and non-consuming provider hints materially reduced measured hot-due work/churn: controlled combined logical gets/completion 293/288 versus 163316/155011, denials 5/3 versus 101/85. Selector-only was faster but caused more denials; admission-only was slower. The million-row sample completed 200 during measurement, 210 with warmup. It did not reconcile a million rows. [Current raw evidence](../validation/qualification/2026-10-21-queue-optimization/README.md) retains the earlier interrupted full and all baseline artifacts.

Production remains **NO-GO** while applicable [parent release gates](../TODO.md) remain incomplete. The tested single-datasource rollback gap is closed for the injected cases, including physical Oracle commit rejection, old leases and local revisions. Independent-process/crash/racing-insert proof, production-like capacity/SLOs, real provider budgets, timeouts, operations/security, consumer/compatibility/supply-chain and retention gates remain open. The earlier Q2 selection-cost finding is preserved as history. Q3 does not show uniform improvement: gathered-stat all-future probes used 1859 versus legacy 1710 logical gets. Index/history/polling economics, ambiguous per-cursor reload counters and sustainable multi-pod capacity remain open; use callback selector counts and native session deltas. The test-only owned-schema DDL teardown is not production cleanup performance.

### Q4 continuous-worker qualification

Source `fad6874c9fa0e0a16ed7d542cace11ea89a991e1`, tree `a530a260738510da84ed7f68fd26a8a75b803d67`, full run `1afffb7d-52ca-4d4a-8857-23c3cefb8f4c` passed:113 Surefire/27 Oracle tests, PIT160/162, six semantic Gatling simulations/367 HTTP requests/zero errors. Default local/CI passed111 JUnit tests. The actual auto-configured worker/JPA/JDBC probe completed160 measured reconciliations per mode in18061ms fixed/9467ms bounded pacing. Warmed empty polling15.00→3.99/s and native gets3493→998.75/s; ten phases passed with stable physical-session endpoints. Cursor attribution records1116 valid/42 unattributable intervals, preserving all raw data and prior corrections. [QM](../QM.md) and [raw evidence](../validation/qualification/2026-10-22-worker-pacing/README.md) define the exact scope.

The accepted selector and scoped mixed-transaction milestones remain established and unchanged; all seven atomicity cases passed again. The million-row coordinator sample completed200 during measurement/210 with warmup, not one million. Its direct5ms runOne loops differ from the actual worker/JPA probe. Small local wake/resource budgets do not establish production SLOs or graceful shutdown under I/O. All parent gates remain open and production stays **NO-GO**; no merge or package publication occurred.

### Q5 shutdown, waits and independent-JVM recovery

Final code `8830c3f6d2ac8483c35309e747e896131c64fcea`, tree
`10cb9835a8f925f9e45a2a437d9f637ee5668ca2`, passed default run
`47311f17-5b2c-409b-ab19-8b9340fc8af5`:117 JUnit/61 Python guards, no expensive
default goals. The completed full tested `3564410ad8e884509cecb0e530a8e3c16473eb29`,
tree`d0af02e4117463eae97a25108b537eb9650fd577`, run
`d0500d1e-030b-425e-9b49-a35e214f7598`: **FAILED**,119 Surefire/45 Oracle tests,
two Oracle failures and zero errors/skips. All seven accepted atomicity cases
and six new independent-JVM checkpoint/crash/recovery cases passed. Eleven of
twelve I/O cases passed, including controlled shutdown, pool/read/owner bounds
and both uncertain-COMMIT outcomes. [QM](../QM.md) and [raw evidence](../validation/qualification/2026-10-22-shutdown-recovery/README.md)
retain exact commands, identities, outcomes and cleanup.

Independent locked JDBC cancellation stayed physically in a socket read after
5001ms despite an effective2s statement deadline. The unchanged5s/ORA-01013 gate
failed; a passing network timeout is not a substitute. The existing worker160-row
measured backlog also exceeded20s in full, after successful warmup; a later
ten-phase targeted worker diagnostic passed. No SQL/pacing redesign or threshold
change was made. Final8830c3f changes only failure-interval diagnostics relative
to that full; its full rerun was **BLOCKED before provisioning** by the unchanged
18GiB host-memory planning budget (four samples17.49–17.85GiB). All full suites
for that unexecuted attempt are NOT_RUN, without borrowed earlier reports.

The starter now distinguishes bounded stop from successful physical drain,
retains owner capacity through late physical completion and refuses restart
while unresolved. The crash proof uses headless public-coordinator JVMs, not
continuous-worker pod crashes; direct fixture pool exhaustion does not qualify
saturated-worker end-to-end recovery. Arbitrary host/network/HTTP-stage behavior,
multi-process contention, operations/security/consumer and other parent gates
remain open. All seven session containers/volumes were independently confirmed
absent. The million-row full fixture completed200 measured reconciliations/210
including warmup, not one million. Accepted Q4 and prior evidence remain unchanged.
Production remains **NO-GO**.

### Q5 resumed full after headroom restoration

The resource prerequisite was cleared at21.648GiB against the unchanged18GiB
planning budget. Full verification actually ran on source
`08bd1906e54d6a182a1a7d2c9473fc255f7038fa`, tree
`a5f5e717c9fcd3331c139558508c312aff901d60`, run
`e88fc1de-755e-470c-a7f7-15c3c7a4f13d`: **FAILED**, Maven/wrapper exit1,
119 Surefire/45 Oracle tests,one Oracle failure,zero errors/skips. Fresh local
default passed117 JUnit/61 Python guards with no expensive goals. The source
remained frozen through collection; only reports/docs change afterward.

All six independent-JVM crash cases, seven retained atomicity cases and ten
actual worker phases passed again. The worker completed160 measured syncs in
17260ms fixed/9737ms bounded pacing within the unchanged20s gate; the earlier
timeout remains historical evidence. Eleven I/O cases passed, but the independent
statement probe still physically read after5017ms despite effective2s timeout.
The unchanged5s/ORA-01013 gate failed, and later assertions in that case are not
claimed passing. Network timeout success does not replace this failure.

PIT160/162, all six semantic Gatling scenarios/367 requests/zero HTTP errors,
migration, concurrency, scale and comparison passed. The million-row seed
produced200 measured reconciliations/210 including warmup, not one million.
No SQL/pacing, resources, workloads or thresholds changed. The container and
volume were independently confirmed absent; all eight session pairs have
absence receipts. [Current QM](../QM.md) and [new raw evidence](../validation/qualification/2026-10-22-shutdown-recovery-rerun/README.md)
preserve the original failed exit, exact identities, partial results and all
previous successful/failed/blocked evidence. Memory is no longer the blocker for
this attempt. Statement cancellation, passing overall full verification and
other applicable parent release gates remain open; production remains **NO-GO**.

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
