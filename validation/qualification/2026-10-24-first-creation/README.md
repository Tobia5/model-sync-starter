# Bounded first-creation pass - 2026-10-24

Final clean source `cfcc53d9bb7a90e20bbd67f4255d8322c1fc0d99`, tree `1a900bb8845c912793fb5136118222a1fac84c8e`.

- Default: `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify`,
  run73cbe204-8173-4287-a77c-d294ff1623d8, exit0/104.851s/collector0;
 155 JUnit/33 XML suites,79 Python guards,0 failures/errors/skips.
- Oracle: `python scripts/full-oracle-container.py --creation-diagnostic`,
  run16a9d2a6-3581-4715-a9d5-895875d3e34e, preflight/launcher/wrapper0,
 143.213s supervision/130.72s infrastructure;12/12 JUnit launcher tests/15841ms,
  zero failures/skips/container failures. Six native INSERT refusals8-75ms with A
  unresolved, two projection/state work-fence refusals12/15ms, four deliberately
  observed DEFAULT waits (not bounded PASS). All A commit/rollback and fresh recovery
  outcomes passed, no failed enqueue/configuration accepted or emergency release.
- Pre-control sourcef55f929/run1ea8bf28-27f0-4225-970e-6298b62e8335:6/6 evidence
  checks observed actual default INSERT waits and demonstrated fence serialization.
  Retained separately from native candidate evidence.
- Source754ba06 default FAILED/exit1 at test compilation (missing Priority import),
 119 earlier JUnit tests passed. Source62a878f default PASS/155 tests, Oracle NOT_RUN
  before passive coverage expansion. Final results are not borrowed across sources.
- Strict detailed evidence validation PASS; old ZIPs/tests and existing point/queue/
  acquire/cooldown bodies unchanged. Both owned container/volume pairs independently
  absent. Default and scoped run summaries plus original/normalized logs included.
- Full/PIT/Gatling/large seed/prototype/A-B-C NOT_RUN. Original UPDATE cancellation
  failure/full requirement and failed-cooldown publication gap remain open. Gate
  acceptance and production readiness are separate from these regression results.

`execution-analysis.json` contains original measurements and all attempt identities,
counts/commands/exits. `raw-evidence.zip` contains immutable per-attempt logs/reports,
run/file manifests, admission/cleanup/validation/preservation receipts and supervisors.
SHA-256 `84c9ff461dc296c5483d9ab8a551c8ddaffa8ea389c1e822a577b028f80db5e4`; 642553 bytes/343 files. CRC/input hashes
verified; packaged-files-sha256.json covers every input. Bounded secret scans found0,
not a universal audit. See [QM](../../../QM.md), [matrix](../../../docs/quality/LOCK-POLICY-COVERAGE.md)
and [reachability](../../../docs/quality/FIRST-CREATION-REACHABILITY.md).
