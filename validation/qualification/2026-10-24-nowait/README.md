# NOWAIT persistence prototype — 2026-10-24

**Default PASS; Oracle BLOCKED / NOT_RUN; no new measured policy decision;
old A/B/C and full NOT_RUN; production NO-GO.**

Clean tested source `ab7ae1d0bfb15670b6de3dc8205573c9fc04844c`, tree
`4601bfdfab27e849bd15ce96647b4689896009f0`, change digest
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
`mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify`:
run `dc582cf4-df8a-4b56-b198-4a7d03f094e8`, exit0/102.511s, collector0;
125 JUnit/27 suites,67 Python, zero failures/errors/skips.

The earlier new-test compile failure at731d558/run
`c37466c0-899e-473b-ae45-662484a9f6e7` remains FAILED, exit1/74.577s, with its
original log and partial reports. It is not an inherited baseline failure.

Planned `python scripts/full-oracle-container.py --nowait-diagnostic` did not
execute. Capacity attempt `b775f679-54e7-4c24-81da-16d13643cff4` observed
11.410/11.402/11.430 GiB available versus the retained12 GiB small-run budget,
2026-10-24T09:07:40–47Z; Python2/enclosing PowerShell1. No session Oracle resource
was created; no unrelated resource was changed. No Oracle run ID or SQL/result
measurement exists for the candidate. Seven prototype tests compile but are NOT_RUN.

[Raw evidence](raw-evidence.zip):133 files/246673 bytes,
SHA-256 `792d6594b80eff76390ce5dda393db2659918245191b79943c352d7c91569eab`.
Original failed/successful default logs/reports, source/run/file manifests,
capacity/helper files, preservation receipt and `blocked-summary.json` retained.
CRC/per-file equality verified; bounded generated48-hex/XML-secret-property scan
found0, not a universal secret audit. No stale Oracle reports are attributed here.

The accepted2c39b75 diagnostic,43040e7 report, original cancellation gate and
three earlier diagnostic ZIPs were checked unchanged. Production stores/interfaces
are unchanged; prototype seams are test-only. See [QM](../../../QM.md),
[audit](../../../docs/quality/NOWAIT-PERSISTENCE-AUDIT.md) and
[unapproved containment proposal](../../../docs/quality/JDBC-CONTAINMENT-PROPOSAL.md).
No merge, main change, full execution or publication occurred.
