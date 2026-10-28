# Corrected diagnostic completed — 2026-10-23

**Default PASS; diagnostic evidence6/6 collected; independent cancellation FAILED;
network client containment observed; server termination unqualified; full NOT_RUN;
production NO-GO.**

Exact clean source `2c39b759cd9295c35eeddf6587094816950ccdf3`, tree
`03f1aa3e069863207151b28f2389f899f089e7fb`, change digest
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
Default command `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify`:
run `2ceaca58-96aa-4e1b-984d-5be4869b1d84`, exit0/113.698s, collector0;
121 JUnit/26 suites,64 Python; zero failures/errors/skips.

`python scripts/full-oracle-container.py --cancellation-diagnostic`:
run `31f8e5ee-aa5f-4ec3-920d-4b952f90c089`, attempt
`56d20d55-638d-40ff-8357-0c720951467f`,175.557s; Maven initialize preflight,
launcher, wrapper and supervisor exit0. Six diagnostic evidence checks passed
in48.145s, none failed/skipped. This is not a passing cancellation/full gate.

| Probe, same one-row locked DML | Direct JDBC client exit | Spring/Hikari client exit |
|---|---|---|
| A query2s/read10s/observation5s |10007ms, FAILED |10013ms, FAILED |
| B same + manual cancel once |10017ms;cancel returned0ms |10008ms;cancel returned0ms |
| C same + network3s |3007ms, client bound |3016ms, client bound |

All physical connections closed after recoverable18730/socket timeout. Oracle's
exact session still waited on the row lock after client exit in6/6. Cleanup
released the blocker; the wait was no longer observed4–9ms later. That is not
independent server termination. Fresh committed row stayed `before` and subsequent
new-connection updates committed in6/6. All owned executors/pools ended, table
absence checked, container `msq-f6dafe3a2248` /volume `msq-f6dafe3a2248-data`
independently absent at2026-10-23T07:46:35.755316Z. No unlock earned cancellation
credit. No full/PIT/Gatling/large seed ran;6 GiB/4 CPU caps unchanged.

[Corrected evidence](corrected-evidence.zip):86 files/158481 bytes,
SHA-256 `392741498cbbfbf1a38b56e37b88497ff319eccc6c77a935c8de8a88b6605f25`.
Fresh raw reports/logs, source/run and file manifests, capacity/cleanup receipts,
`corrected-summary.json` and run-scoped `corrected-QM.md` are retained. The latter
is also generated at `target/qm/QM.md`. CRC/per-file equality verified; bounded
generated48-hex/XML-secret-property scan found0 (not a universal secret audit).
Original038fa579 and first-execution8533f34c ZIPs below remain unchanged.

The [contract draft](../../../docs/quality/JDBC-CONTAINMENT-PROPOSAL.md) remains
unapproved/unimplemented and explicitly separates client containment from the
unqualified server bound. [Current QM](../../../QM.md) records the actual source,
platform/configuration and unresolved cause. No merge, main change or publication.

---

# Actual focused locked-JDBC diagnostic — 2026-10-23 (earlier attempt)

**Latest default PASS; actual Oracle diagnostic FAILED; corrected Oracle run
BLOCKED / NOT_RUN; new full NOT_RUN; production NO-GO.**

Executed source `9b2476653a1f1364ea35d52f916e706910428ff3`, tree
`704b10350a9b6a4b204c9b45daee33c05e7a51ac`, run
`8bf36376-0d7b-45ef-9c12-c503aacdea5f`, container attempt
`e28be974-f96b-4a89-86e7-1f91530dddb4`. Default117 JUnit/64 Python first
passed, exit0. `python scripts/full-oracle-container.py --cancellation-diagnostic`
actually ran: Maven initialize preflight0, launcher/wrapper/supervisor1;
203.290s total,47.683s suite. Six tests started/FAILED; no skipped tests.

| Probe | Direct JDBC client exit | Spring/Hikari client exit |
|---|---|---|
| A query2s/read10s/observe5s |10007ms, bound FAILED |10003ms, bound FAILED |
| B same + manual cancel once |10015ms; cancel returned0ms |10017ms; cancel returned0ms |
| C same + network3s |3002ms |3009ms |

All six raised recoverable18730 with a socket-timeout cause and closed their
physical connections. C is an observed client bound, not independent statement
cancellation or complete containment qualification. The first classifier rejected
18730 before fresh-state/subsequent-work checks, so those checks are NOT_RUN.
The raw C field PASS is intermediate; case INCOMPLETE/JUnit FAILED remains the
actual qualification status. Exact server state before blocker release was not
measured. No lock release is credited as cancellation.

All owned threads/pools ended and the table was removed. The exact owned container
`msq-c9233f5409d1` and volume `msq-c9233f5409d1-data` were independently confirmed
absent. No production or unrelated resources were changed.

Corrected source `6edcf844ff1d9bdae5876d918e22c31b9619a346`, tree
`0c17f0fcb1f40c310334281ed0edfc502e9322c0`, default run
`76243cef-7f67-4a57-8937-5062c1d70274`: exit0/92.497s, collector0,
121 JUnit/26 suites,64 Python, zero failures/errors/skips. Four cheap regressions
protect narrow error classification. Its Oracle execution did not run: scoped
capacity attempt `a3348b29-b137-4a90-9c41-5f09f777906b` found11.474–11.493 GiB free
versus the declared12 GiB small-run budget. Full18 GiB planning is unchanged;
small JVM is capped512 MiB heap/128 MiB direct, Oracle6 GiB/4 CPU remains unchanged.
The first scoped run had12.316–12.371 GiB available. Earlier18 GiB block below is
retained, not erased or attributed to the executed run.

[Execution evidence](execution-evidence.zip):154 files/289658 bytes,
SHA-256 `8533f34c4b64e25c3f294976aeaabd262341ded358256aba369ccfb72dcfcb40`.
Contains original failed-run measurements/log/exits, two separate default archives,
source/run manifests, capacity/cleanup receipts and `execution-summary.json` with
input hashes. CRC/per-file byte equality verified; bounded generated48-hex/XML
secret-property scan found0. This is not a universal secret-detection guarantee.
Original active failure contract, test/configuration and baseline ZIP hashes were
verified unchanged. No full qualification was repeated.

See [current QM](../../../QM.md) and the
[unapproved containment draft](../../../docs/quality/JDBC-CONTAINMENT-PROPOSAL.md).
The corrected Oracle safety measurements must execute before a replacement can
be accepted or implemented. No merge, main change or publication occurred.

---

# Focused locked-JDBC diagnostic preparation — 2026-10-23 (earlier attempt)

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
