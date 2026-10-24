# Executed NOWAIT persistence diagnostic - 2026-10-24

**Default PASS; selected diagnostic PASS (7/7); full NOT_RUN; production NO-GO.**
The original cancellation gate remains failed/open. Production adapters and defaults
are unchanged. No old A/B/C matrix, large seed or full qualification ran.

Final clean source `eb182094dc168573accb8c12f8e63264f771f192`, tree
`904b24d22194b2e4c303ce14538e8368f711b3ec`; default run
`ff2ad62c-9be7-40b2-bb7a-927db14c5036`, exit0/88.319s, collector0:
125 JUnit/27 suites,67 Python, zero failures/errors/skips.
`mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify`.

Then `python scripts/full-oracle-container.py --nowait-diagnostic`, run
`c82fa300-a9e4-4a6e-ab9d-274d4f824936`, infrastructure attempt
`400fa8cf-c384-4bd3-bdba-4500941af774`: preflight/launcher/wrapper exit0,
120.358s wrapper supervision,5581ms JUnit,7 started/passed,0 failures/skips.
The wrapper used the same project settings and invocation-only disposable
acknowledgments; its Maven preflight was `-Pfull -N initialize`, not full verification.

All five refusal cases passed three attempts each while the blocker remained held:

| Case | Refusal ms | Outer transaction end ms | Previous Oracle SQL ID |
|---|---|---|---|
| JDBC point SELECT NOWAIT |17,5,4 |66,8,7 |`4rrcx6pxm100r` |
| JPA mapping lookup NOWAIT |6,7,6 |9,10,9 |`apad3w6hckwc8` |
| Real JPA refresh NOWAIT |10,8,7 |14,11,10 |`d7btmgvwcr0ak` |
| Extra-row flush UPDATE NOWAIT |12,10,10 |16,13,14 |`a5nx5hysw1v17` |
| Racing projection INSERT NOWAIT |13,8,7 |16,10,10 |`c57ndxp9jwvtx` |

Times begin before attempt transaction setup; they are not isolated execute latency.
Each produced Oracle54. Exact SID220/serial8623 was INACTIVE, SQL*Net idle,
LOCK_WAIT0/TX_ACTIVE1 before rollback; after rollback LOCK_WAIT0/TX_ACTIVE0.
Earlier ledger guard writes/locks rolled back, no success state appeared, target
token/generation persisted, physical/pool-active returned0, connections stayed open
and subsequent pool queries succeeded. Recovery/old-token rejection followed
cleanup with explicit fixture expiry; not natural-lease timing proof. Queue
SKIP LOCKED and generation/fencing/local-revision controls also passed; they do
not contain separately measured refusal/transaction timing or session traces.

The existing-row policy is supported within this fixture. Native DML support is
separately demonstrated only on Oracle26 for this nonbatched single-table mapping.
Additional tables, custom SQL, new ledger/state/quota insert races, FK/deferred
constraints, cascades/triggers, older databases, transport/COMMIT uncertainty and
universal server cleanup remain unqualified. No production policy or replacement
contract is implemented or approved. See [QM](../../../QM.md) for all seven cases,
SQL, retries, exact configuration, resource admission and the measured decision.

## Preserved first attempt

Clean `a7a2bcf612057b363d33e985a81d50377751061e`, tree
`e7a13f272f39d0d701a32eaaefe7f27f8eccac21`, default run
`13e65e23-efb2-4bf0-b727-64eb92d4dc6f` passed125 JUnit/67 Python,
exit0/83.465s, collector0. First diagnostic
`e96cd2cc-9dc7-40d9-9c25-4ea2e46c7625` **FAILED**, exit1/127.005s:
3 passed/4 failed at claim before their intended operation. The queue-only
control left its local provider reservation occupied. The final test-only fix
disposes that claim through existing failure/defer semantics and proves a
follow-up claim; it does not alter production scheduling or admission.
The failed run and original exits/reports are retained, not relabelled PASS.

## Artifacts and cleanup

[Raw evidence](raw-evidence.zip): **176 files/334715 bytes**, SHA-256
`0b9c3594e9e6fc49d5378956d5293dc7a307bf749f3e85e3968a71a50d091919`.
Contains both defaults and diagnostics, original/normalized logs, raw JSON/XML,
source/run/hash manifests, admission and independent cleanup receipts, pinned
settings/helpers, preservation receipt and the run-scoped QM. ZIP CRC and every
input byte/hash verified. Bounded generated48-hex/XML-secret-property scan found0;
this is not a universal secret audit.

[Derived analysis](execution-analysis.json) retains case timing definitions and
source attribution; [actual observed SQL](observed-sql.sql) removes only duplicates
and the metadata query itself. Raw queue cursors include that metadata query
(`fqkda3cr4fg4q`); cumulative execution counts are not window deltas or selector
counts and support no performance claim. Original snapshots remain intact.

Post-build admission passed once per source: a7 minimum12.555 GiB, eb minimum13.037
GiB against unchanged12 GiB. Full planning remains18 GiB. Oracle6 GiB/4 CPU and
JVM512 MiB heap/128 MiB direct were retained. Generated credentials, synthetic data,
separate schemas and loopback ports only. No unrelated resources changed.

Containers/volumes `msq-005024c39cfa`/`msq-005024c39cfa-data` and
`msq-4999761759ec`/`msq-4999761759ec-data` were independently confirmed absent.
Owned pools/executors ended, table removed, physical remaining0. No emergency
release occurred; releasing blockers after proof was cleanup only. No owned residue
observed. Earlier [blocked evidence](../2026-10-24-nowait/README.md), `ab7ae1d`,
`a7a2bcf`, `2c39b75`, `43040e7` and all earlier ZIPs remain unchanged.
