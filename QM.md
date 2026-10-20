# Model Sync quality evidence

## Default verification: PASS

PR [#1](https://github.com/Tobia5/model-sync-starter/pull/1) source commit `9a4d94506dd17a1108bc7a3f0f75526baa8d7de0` has tree `e6411fd289064f6ffdc7a42095bda68e52aeed91`. The [default workflow run 39416329845](https://github.com/Tobia5/model-sync-starter/actions/runs/39416329845) tested PR merge commit `e01fbc4879cbf7c3e41cdbeb62c299b1fe279a2c` with that **same tree**. Its checkout was clean. `./mvnw -B -ntp clean verify` exited **0** on Ubuntu 24.04, Temurin Java 21.0.12.1, Maven Wrapper 3.9.9 and Python 3.12.3.

The run reported **50 JUnit tests in 13 Surefire XML files**, with **0 failures, 0 errors and 0 skipped**. Both compiled-output ArchUnit tests passed; the JDBC/H2 module reported 16 tests including the new inconsistent-ID/zero-row claim regression. The build and default-goal audit recorded no PIT, Gatling or Failsafe goal. The workflow's [run-scoped QM and raw artifact](https://github.com/Tobia5/model-sync-starter/actions/runs/39416329845/artifacts/12261447714) includes `target/qm/QM.md`, `evidence.json`, `run-context.json`, `raw-manifest.json`, the Maven log and the XML files. Evidence run ID: `2be1a7ab-05aa-49d7-9f83-392da58dfe1b`; the manifest hashes every admitted raw XML report.

## Full qualification: BLOCKED / NOT_RUN

`./mvnw -B -ntp -Pfull clean verify` was **not executed**. On this workstation, `python scripts/full-preflight.py` exited **2** because dedicated Oracle runtime/migration connections and the disposable-environment acknowledgments are unavailable. The GitHub API lookup for the workflow's `model-sync-full-nonproduction` environment returned HTTP 404, so no manual full run was dispatched. The explicit container mode is implemented, but it was not opted into or executed in an approved disposable runner. No full result or test count is inferred from old output.

PIT mutation counts and score: **NOT_RUN**. JUnit Platform 6.0.3 / PIT JUnit plugin 1.2.3 runtime compatibility: **UNVERIFIED**. Oracle smoke, migration, concurrency, 100,000-row ledger-claim and million-row reconciliation workload: **NOT_RUN**. Gatling's six in-memory semantic simulations: **NOT_RUN**. There are no measured Oracle completions, owner-call counts, database-work totals, p50/p95/p99 latency, reconciliation lag or capacity results. The 85% killed/generated PIT gate remains unchanged.

The full workflow is `workflow_dispatch` only. External mode requires approved separate `MS_TEST_` schemas; container mode requires explicit `disposable-container` selection, a digest-pinned Oracle Free image, generated per-run credentials, separate runtime/migration schemas and loopback-only access. The new Oracle workload uses four workers and a target of 230 synthetic owner calls after a one-million-row seed; concurrent workers may make a few final calls beyond that target. Its 150-completion floor checks persisted projection outcomes, **not** one-million completed refreshes or a production SLO. None of that is evidence until a full run supplies raw reports and passes the full gate.

## Local and static checks

The local dependency-enabled `./mvnw -B -ntp clean verify` attempt stopped at module 8/15 because configured Nexus host `kubernetes-dev` could not resolve Reactor 3.8.7 POMs; no alternate credential or repository was used. A focused offline `./mvnw -o -B -ntp -pl model-sync-persistence-jdbc -am test` subsequently exited **0** and ran the JDBC/H2 tests, including the claim regression. The local full default result remains **BLOCKED**; the exact-tree CI run above supplies the default PASS.

At the review source tree, `bash scripts/test-triggers.sh` passed **77 core and 41 trigger contract assertions** (these are not JUnit counts); `java scripts/SourceSyntaxCheck.java .` parsed **165 files with 0 syntax errors**; the Python guard suite passed **28 tests**. Source architecture, documentation, profile and effective-lifecycle audits passed. These checks do not replace dependency-linked compilation or full Oracle/PIT/Gatling execution.

## Production readiness: NO-GO

Default verification does not qualify Oracle behavior, full mutation strength, load capacity, provider isolation, operational recovery, vendor adapters or stable packaging. The original P0 gates in [TODO.md](TODO.md) remain open. No package was published, no PR was merged and no production data was touched. This root report is a later documentation-only commit and names the exact source tree tested above; it does not claim that its own future commit was tested.
