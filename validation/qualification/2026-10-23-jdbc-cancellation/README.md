# Focused locked-JDBC diagnostic preparation — 2026-10-23

**Default PASS; Oracle diagnostic BLOCKED / NOT_RUN; new full NOT_RUN; production NO-GO.**

Clean tested source `5b898c3694ef6f9a287e2d2bba0bcde955181deb`, tree
`8d28ebead9413f98af9dc6a4385ed90b0c78f919`, empty-change SHA-256
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.

Default command: `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify`.
Run `177ed7a8-1c51-459f-913b-74a22f48ecf9`, exit0/106.067s, collector0;
117 JUnit /25 suites, zero failures/errors/skips,64 Python guards.
The subsequent report commit is not claimed as the tested source.

The planned `python scripts/full-oracle-container.py --cancellation-diagnostic`
**did not execute**. After Maven exited, resource attempt
`ed85ad37-0c24-4f7f-9ff4-da8951a58446` observed12.185 GiB free at
2026-10-23T07:17:23.6328513Z, below the unchanged18 GiB planning budget.
Python preflight exit2; enclosing PowerShell pipeline exit1. No session Oracle
container, volume, schema or database was created. Existing unrelated resources
were not changed. Earlier concurrent-build resource observations are retained.
Cases A/B/C each remain NOT_RUN: query2s/read10s/observe5s, explicit cancel once,
and separately configured network3s containment. No measured cause or substitute
statement-cancellation PASS is claimed. See [QM](../../../QM.md) for scope/boundaries.

[Raw evidence](raw-evidence.zip):73 files,137653 bytes;
SHA-256 `038fa5790cf7d98eb862336977f252542577ddbe67f061d1683a07779c3165a7`.
Contains original and UTF-8-normalized logs, fresh JUnit/QM reports, source/run
manifests, local supervisor and capacity helpers/receipts, and `summary.json`.
The summary hashes every retained input; ZIP CRC and archived byte equality
were verified. Bounded checks found no generated48-hex credentials or nonempty
secret-like XML properties; they do not constitute a universal secret audit.
No old Oracle/PIT/Gatling output supplies measurements to this attempt.

Earlier baseline archives and the complete report at `1b0cc69` are unchanged.
No merge, main change, workflow dispatch or package publication occurred.
