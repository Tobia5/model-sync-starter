## Named configuration and rejected-qualification diagnostics

[QUALIFICATION-TARGET](QUALIFICATION-TARGET.md) and its method inventory constrain
full qualification to the owner-approved Oracle26 NOWAIT/INSERT configuration.
Missing methods, DEFAULT runtime fallback, wrong platform/mapping/enlistment,
stale receipts or all-configuration claims cannot pass. Exactly one preserved
plain-UPDATE mechanism is separate driver characterization: historical FAILED,
current NOT_RUN unless executed. All other applicable assertions remain mandatory.
A successful Maven exit with rejected qualification still writes fresh diagnostics,
keeps the original build exit and records a separate qualification failure/exit.
A blocked admission has no Maven full exit or inherited previous suite results.

# Quality report contract

Each CI run writes `target/qm/QM.md`, `target/qm/evidence.json`, command log, and raw Surefire/Failsafe/PIT/Gatling/Oracle reports into a run-scoped Actions artifact. A short job summary uses the same generated evidence. The repository-root `QM.md` is a reviewed snapshot tied to its explicitly named tested source commit/tree; a later documentation-only commit does not silently reassign that evidence.

Statuses: **PASS** requires exit 0 and all required raw reports; **FAILED** means a check ran and failed; **BLOCKED** means a required service/input prevented meaningful execution; **NOT_RUN** means no attempt. `NOT_APPLICABLE` requires a reviewed scope rationale and is not used for the declared full gates.

Record actual command and exit, JDK/Maven/Python/Boot/JUnit/PIT/Gatling versions, discovered JUnit suites/counts/skips, PIT target/exclusion manifest and raw status counts, owner calls and ledger results, Oracle version/schema/migration result and scale database work, dataset/mix/concurrency/rate/latency/resources, warmup/measurement/drain, request latency percentiles/errors, completed-sync throughput and lag. Missing measurements remain explicit; accepted HTTP responses never count as reconciliations.

The `check-test-reports.py` gate rejects absent, malformed, stale, empty, failed or skipped default suites. `check-full-reports.py` additionally requires the named Oracle suites, PIT XML/HTML with at least 85% killed/generated and nonzero mutations, six Gatling logs/HTML and owner/ledger assertions, and Oracle scale evidence. Full preflight rejects test/PIT/Gatling/Failsafe skip and filter flags. Publishing a stable artifact requires independently reviewed passing full evidence and artifacts for the exact requested source SHA/configuration; this task only publishes reports.
