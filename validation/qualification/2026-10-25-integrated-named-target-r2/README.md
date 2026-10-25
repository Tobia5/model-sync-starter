# Integrated named-target qualification - actual failed run, 2026-10-25

**Default PASS. Configured full FAILED. Production NO-GO.** Clean tested source
`25297fd61724b22cc1c63f2ce098a410f584a1dc`, tree
`56967533013048be55e65a8a8a901e61ab18b6fb`, on PR #1's existing review branch.
Target: `oracle26-nowait-ledger-first-v1`, named configuration only. No product,
qualification-tooling, applicability, threshold or workload change was made.

## Attempts and original outcomes

| Attempt | Actual results |
|---|---|
| Fresh default `365e19af-dbc6-4387-a912-3a608aaefe6f` | Build0 / qualification0 / collection0 / recorded-execution audit0;160.712s.156 JUnit /34 XML suites,84 Python guards, no failures/errors/skips.102 recorded plugin goals, none expensive. |
| Post-build admission `bf902018-5614-470c-9df4-a606a46f5081` | PASS/exit0. Host20.418/20.419/20.414GiB versus unchanged18GiB. Docker unallocated19.921GiB; diskC93.144/D1069.959GiB. One bounded check. |
| Full `5fa399f7-2adb-44be-8e57-c89962f1b8b0` | Maven1 / wrapper1 / supervisor1 / qualification1 / collection0;5757.381s.103 passing Surefire tests /22 XML suites;19 Oracle methods /7 suites,18 passed,1 failed,0 errors/skips. |
| Full inventory |77 methods /16 suites remain required.58 methods /9 sample suites and effective target-host receipts NOT_RUN because Maven skipped the dependent sample after JDBC failure. Old default reports were REJECTED as stale, not counted. |
| Independent full modules | PIT165 killed /166 generated,99.3975903614%; one reviewed equivalent survivor, no no-coverage/timeouts/run errors. Six Gatling semantic scenarios PASS,367 HTTP requests/0 KO, separately asserted owner/ledger outcomes. |
| Cleanup | Wrapper verified removal; independent read-only inspections confirmed the owned container and volume absent. No residual session-owned Oracle resource. |

The default command was `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify`.
The full entry point was exactly `python scripts/full-oracle-container.py`, with
the two approved disposable acknowledgments and the same settings forwarded to
`mvnw.cmd -B -ntp -s <approved-settings> -fae -Pfull clean verify`.
Git Bash `scripts/test-triggers.sh` separately passed86 core /43 trigger assertions
in4.234s; those assertions are not additional JUnit tests.

## Concrete failure and partial measurements

`OracleOptimizationIT.comparesIndependentChangesOnTheSameOraclePopulation`,
line212, failed `ledger_attempts == quota_grants + quota_denials`:
**426 !=220+151**. The quota-lock cursor has established continuity and426
executions /371 fetches; the session snapshot records55 rollbacks.
`MeasuredQuotaGate` increments only after `delegate.acquire()` returns, while a
claimed attempt can fail before receiving a decision. Per-attempt exception codes
were not retained: this evidence does not independently classify all55 as ORA-00054.
Measured owner calls220 matched grants220; this is not evidence of unadmitted I/O.
Only baseline round0 reached measurement; the other seven comparison measurements
did not run. No comparison PASS, optimization regression or speedup is inferred.
See [failure analysis](failure-analysis.json) for raw cursor endpoints and limits.

The separate capacity case PASSED:1,000,000 seeded /100,000 due, four coordinator
threads with5ms sleeps/pool8,230 total physical synthetic owner calls,210 committed
syncs including10 warmup; **200 measured syncs in9.844s**,20.3169/s. Seed4606.905s
and cleanup are separate. Long reconciliation lag includes the seed-age origin;
a million-row seed is not a million completions. The actual continuous JPA worker,
fault/recovery and atomicity suites did not execute in this attempt.

The100k scale case passed8 distributions /24 current and24 legacy claim samples;
these are ledger selections, not provider-admitted calls. Native session metrics,
cursor snapshots/attribution and actual row-source plans are retained. Capacity
measurement has14 VALID /3 UNATTRIBUTABLE cursor intervals; ambiguous intervals
are not converted to deltas. Historical counter corrections remain unchanged.

## Raw evidence layout

[`raw-evidence.zip`](raw-evidence.zip) contains421 entries,2,520,898 bytes,
SHA-256 `f1145759e81d880197ac9e7ff3e9023cef6dd9845564f1ae05f76e859d114fbd`.
[Package verification](package-verification.json) records CRC, every input hash,
bounded nonsecret checks and all14 prior archives unchanged. These checks are
not a universal secret audit. Sourcef9f6551/report09f371c and all prior evidence
remain unchanged; the earlier root QM is preserved under the new current report.

- `attempts/default-f23fc5a8-a492-4c4a-88b0-68f1122b16ff/`: original command/log,
  exits, fresh marker, XML, lifecycle/default-goal audit, run-scoped QM and manifest.
- `attempts/full-b1a94ab5-d089-4607-a6a7-03d7be0e31f3/`: original full log/exits,
  fresh marker, failing and passing XML, PIT XML/HTML, six Gatling logs/HTML/assertions,
  Oracle plans/counters/partial comparison, cleanup receipt and run-scoped QM.
- `source-contracts/`: exact tested-source manifest, policy contract, POMs, settings,
  wrapper, validation/report tooling and unchanged characterization source.
- `supervision/`: frozen identity/prior hashes, admission, resource observation,
  independent cleanup, supervisors and analysis. `package-manifest.json` binds inputs.

The full run-scoped QM's separate default-command NOT_RUN means no separate
default invocation under that full marker. The successful matching default is
retained in its own attempt. Stale skipped-module reports are rejected; fresh
executed Oracle failures remain FAILED with their measurements. Collection0
does not change Maven/wrapper1 or qualification1 into success.

[Compact execution index](execution-analysis.json) and [mutation review](mutation-review.json)
retain exact source/run identities and measured counts. Independent plain-UPDATE
cancellation remains historically FAILED at2c39b75/report43040e7; current separate
characterization is NOT_RUN. No old A/B/C matrix or prototype was rerun.

**Next concrete blocker:** record acquisition failures/pre-admission outcomes and
prove complete counter attribution with deterministic regressions, preserving
the committed-admission and physical-call controls. No unchanged full retry was
made. All77 methods, runtime receipts and other mandatory gates remain intact.
Provider cooldown publication/recovery, security, operations and other release
requirements remain open. DEFAULT/older Oracle/arbitrary mappings receive no
bounded-persistence promise. No merge, main change, bootstrap or publication.
