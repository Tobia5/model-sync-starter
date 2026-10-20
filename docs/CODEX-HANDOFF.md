# Codex/cloud-worker implementation task

**Prepared task, not a launched worker.** The review session could read GitHub but was denied branch and issue creation with HTTP403. This document does not bypass that permission boundary. In an authorized Codex environment, select `Tobia5/model-sync-starter` and submit the task below with the cumulative patch bundle accessible to the worker. Do not provide tokens in task text.

The verified main baseline was `a555ffaf8681b6dcc2597f16a84bf5b2a45c11d6` on 2026-10-20. Fetch the actual current branch before editing. If it differs, review the diff and port changes rather than overwriting newer work. This update supersedes the earlier arc42/trigger patch; do not apply both cumulative patches.

## Task to submit

You are working in repository Tobia5/model-sync-starter. Implement and qualify a reviewable hardening/build change, then commit it on a new branch and open a PR using the environment's authorized repository connection. Do not force-push main or auto-merge. Do not publish production packages or operate on production data.

### Phase A — establish evidence

Read AGENTS.md, TODO.md, docs/PRODUCTION-READINESS.md, docs/ARC42.md, docs/USER-HANDBOOK.md and docs/PACKAGING.md. Verify actual HEAD and the current working tree. Apply the provided cumulative update only after its base/content checks pass; preserve unrelated changes. If files are already present, inspect and reconcile instead of applying blindly.

Run the complete build with Java21 and the repository's declared Boot platform. Do not silently downgrade/change platforms to hide incompatibility; investigate and document a necessary compatibility decision. Run:

```bash
bash scripts/test-triggers.sh
python3 scripts/check-architecture.py
python3 scripts/check-docs.py
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
java scripts/SourceSyntaxCheck.java .
mvn -B -ntp -Plibrary-artifacts clean verify
python3 scripts/check-test-reports.py
```

Fix compilation/framework/integration failures with regression tests. Dependency-free assertions, source parsing and generated test files do not replace execution. Do not remove tests, introduce blanket skips, disable Javadoc failures or weaken failing safety checks to obtain green CI.

### Phase B — production-critical behavior

Implement missing tests and narrowly scoped fixes for R02/R03/R04/R05/R06 in TODO. At minimum, validate:

- Two Oracle workers never complete the same active lease; reclaimed/expired workers cannot overwrite newer work.
- Concurrent enqueue and a new source event during fetch preserve intent; passive visits preserve cooldown/backoff and DEAD quarantine.
- Projection writes and ledger completion rollback together under the configured JpaTransactionManager/datasource. Lost local revision prevents stale writeback.
- Job, event and visit triggers converge on shared physical request admission. Periodic versus ON_DEMAND recurrence remains independent of how requests arrive.
- Fresh visits make no owner HTTP call and do not renew validation time. Stale-serving policy and pending/quarantined results are explicit; strict read-after-event consistency is not falsely claimed.
- Migration v1 to v2 works on a disposable Oracle instance, with restart, fencing and rollback limitations recorded. Use dedicated test schemas only and explicit cleanup of test-owned records.
- Slow/hung blocking adapters, 429, database outage and shutdown do not create unbounded threads/queues or leak DB transactions. Measure lock/statement waits and quota-denial churn.

Use the cloud environment's approved disposable Oracle capability. If that is not available, preserve the release blocker and still complete build/code/test improvements that can actually be run. Do not claim H2 as Oracle proof.

For scope claims involving all external IDs, brokers or IBM MDM/Vista/DWH, close R07/R08/R09 respectively or explicitly keep those capabilities out of the supported release. Consider Spring Batch inventory/checkpointing and Spring Integration/Modulith event facilities before building parallel frameworks; do not rewrite the core on speculation.

### Phase C — packaging and CI

Keep ordinary PR/main CI read-only. Verify pinned actions and tests/source/Javadoc packaging. Keep package publication manual, main-only, environment-reviewed and SNAPSHOT-only while production gates remain open. Verify that all parent/runtime/starter module artifacts are resolvable and the sample is excluded. Add a fresh external consumer smoke build with an isolated local Maven repository. Do not use a producer-reactor install as proof that GitHub Packages consumption works.

Do not create secrets, widen repository permissions or circumvent denied APIs. Document the precise required access. A successful local commit is not proof of a remote push; report only real remote URLs/SHAs.

### Required deliverables

Create small reviewable commits for trigger/documentation integration, build/package setup and any runtime fixes, each with tests. Open a PR and attach exact commands/logs, test counts/skips, environment/dependency versions, migration/load results and remaining open gates. Update TODO checkboxes only where actual evidence exists. Record CI run IDs and distinguish local, remote, skipped and blocked outcomes.

Never call the result production-ready merely because CI builds a JAR. Stop stable publication at unresolved applicable P0 gates. Leave any remaining work as concrete acceptance criteria rather than vague "improve tests" notes.

## Launch reference

[Official Codex Cloud task workflow](https://developers.openai.com/codex/cloud): choose a published environment/repository, start a task, review changes and test results, then commit/open a PR through the authorized environment. A GitHub repository connection alone is not evidence that this task has started.
