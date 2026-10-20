# Model Sync quality evidence

## Current default CI result: PASS

[GitHub Actions run 39409847728](https://github.com/Tobia5/model-sync-starter/actions/runs/39409847728) completed successfully for PR head `5d54df5d115f18aa95671b0c04198697af368de9`. The pull-request checkout was merge commit `970df8d50147d20f11edcbcd3afc721b4a048fd5`; its tree `73c312d375b1d9f918121abf36743c8e6e820f0f` equals the head tree (remote main `a555ffa` is an ancestor). The exact `./mvnw -B -ntp clean verify` command exited 0 on Ubuntu 24.04, Temurin 21.0.12.1 and Maven Wrapper 3.9.9. The run produced 49 Surefire tests across 13 XML files, with 0 failures, 0 errors and 0 skipped. Both compiled-output ArchUnit tests passed. The default execution audit and effective profile audit passed without expensive goals.

The run-scoped [QM and raw report artifact](https://github.com/Tobia5/model-sync-starter/actions/runs/39409847728/artifacts/12259490238) contains `target/qm/QM.md`, `evidence.json`, the Maven log and Surefire XML. The workflow's own QM names the tested merge SHA/tree. This later root report commit changes documentation only; it does not reassign that tested tree.

**Full qualification: BLOCKED/NOT_RUN.** The local disposable-environment preflight exited 2 and no authorized Oracle schema was available. PIT, Oracle migration/concurrency/scale, and Gatling have no measured reports. A passing default CI run is not release qualification.

## Local verification: BLOCKED

Generated: 2026-10-20T10:41:08.936236+00:00
Tested source commit: `408fc3db5b4033f088fc9c9c8763860bb12afeed`; tree: `1bdb589c6a8e4f425c20157e8573c8776c10e2b8`.
Working tree dirty at report generation: false.
Environment: openjdk version "21.0.12.1" 2026-08-18 LTS; Apache Maven 3.9.9 (8e8579a9e76f7d015ee5ec7bfcdc97d260186937); Python 3.12.10.

## Commands and outcome

| Lane | Exact command | Exit | Status |
|---|---|---:|---|
| default | `./mvnw -B -ntp clean verify` | 1 | BLOCKED |
| full | `python scripts/full-preflight.py` | 2 | BLOCKED |

## Measured evidence

Surefire: 30 tests, 0 failures, 0 errors, 0 skipped across 7 XML files.
Failsafe: 0 tests, 0 failures, 0 errors, 0 skipped across 0 XML files.
PIT: NOT_RUN.
Gatling: NOT_RUN; no latency percentiles or completed-throughput measurement.
Oracle scale: NOT_RUN.

## Baseline and other checks

The checkout began clean at local main `a36b3ab88ef5200fb51bd2b55956c302ae5a0392`, two commits ahead of observed `origin/main` `a555ffaf8681b6dcc2597f16a84bf5b2a45c11d6`. Those local commits already contained the job/event/visit extension, ON_DEMAND recurrence, arc42, handbook, TODOs and snapshot packaging. No `files/` cumulative bundle was available, so no cumulative or older arc42 patch was applied. `CHECKSUMS.sha256` is a historical archive manifest, not a hash of this refactor.

The baseline Maven build initially failed H2 check constraints; commit `150a526` repaired the test schemas and Windows offline scripts. The next baseline full reactor stopped at the same configured Nexus DNS blocker. The current default attempt reached seven successful modules before Reactor dependency resolution failed. The 30 Surefire tests include 15 JDBC/H2 adapter tests. The report audit counted 44 default Maven plugin goal executions with no PIT, Gatling or Failsafe goal.

Against the tested Java tree, `bash scripts/test-triggers.sh` passed 77 core and 41 trigger contracts; `java scripts/SourceSyntaxCheck.java .` parsed 161 files with zero syntax errors. The default build ran the source/module architecture guard, documentation guard, effective profile audit and 23 Python guard tests. The ArchUnit negative fixtures passed in a separate focused one-test run; the complete compiled-output ArchUnit rule was blocked by missing later module outputs and is not counted as PASS.

Configured platform versions: Java 21, Spring Boot BOM 4.1.1, Maven Wrapper 3.9.9 with SHA-256 pinned distribution, Surefire/Failsafe 3.5.3, ArchUnit 1.5.0, PIT 1.25.9 with JUnit plugin 1.2.3, Gatling 3.13.1 with Maven plugin 4.11.0. The cached Boot BOM resolved JUnit Jupiter 6.0.3 for the passing subset. PIT compatibility with that JUnit Platform remains unverified; the complete default Reactor/Spring build passed in CI above.

The declared PIT targets are freshness and selected application orchestration classes in `model-sync-testkit/pom.xml`; DTOs, generated code, demo and framework internals are outside scope. The required score is 85% killed divided by all generated mutations; no mutation XML exists, so killed/survived/no-coverage/timeout/run-error counts are NOT_RUN. No survivor was reviewed or excused.

The six Gatling Java simulations and the 100,000-row Oracle admission probe are configured but NOT_RUN. The local harness declares 40 fresh visits, 100 hot visits, 20 unsafe visits, one event/job interaction, one cooldown/recovery flow, and 200 concurrent lease job requests over a 200-row synthetic ledger. These are local test-store scenarios; there are no measured owner rate/latency, p50/p95/p99, error rate, completion throughput, reconciliation lag, Oracle resource, warmup/drain or large-data capacity results. No HTTP 202 was counted as a completed sync.

Local raw evidence is in `target/qm/default-build.log`, `target/qm/full-preflight.log`, `target/qm/evidence.json`, and current module Surefire XML. The GitHub run and artifact linked above are separate CI evidence. No full report or package release is claimed.

## Scope and blockers

A default PASS does not qualify full release behavior. Full requires nonzero Oracle migration/concurrency/scale suites, selected PIT scope at 85% killed/generated, and all six Gatling reports. See `scripts/check-full-reports.py`.
The local Gatling harness uses an in-memory test store; its data is not Oracle capacity evidence.
No package publication or production deployment is authorized by this report.

- Default reactor stopped at module 8/15: configured Nexus host kubernetes-dev could not resolve reactor-core/reactor-test 3.8.7. No alternate credential or repository was used.
- Full clean verify was NOT_RUN locally after the blocked local fast build; no authorized disposable Oracle inputs are configured, and preflight exited 2. The later CI default PASS does not supply those inputs.
- The local ArchUnit full scan was blocked by missing module outputs; the separate GitHub CI scan passed. PIT, Gatling, Oracle migration/concurrency/scale, and PIT/JUnit Platform 6 compatibility remain unverified.
