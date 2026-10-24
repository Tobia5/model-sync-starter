# Queue selector and admission qualification evidence

Tested code `ede199a33ad3a0ec5c911110bb84fd7407af1c25`, tree `7c9577e9de49a2f96b1093a65673250a46163da6`. Full run `2b75d9cc-8836-4e99-b227-9d836e7e207a`, attempt `c3a564fe-e255-4b85-a209-05244d9b86ec`: **PASS**, Maven 0 / wrapper 0, 5623.33 s. Default 103 JUnit; full 105 Surefire / 26 Oracle tests, zero failures/errors/skips; PIT 160/162; six semantic Gatling runs,367 HTTP requests / zero errors. Production remains **NO-GO**. See [QM](../../../QM.md).

- [Raw evidence ZIP](raw-evidence.zip): 3,774,804 bytes, SHA-256 `e4c2878e674a9331dfda4d6dd89dd8bc97b8f5b5d42554869611323cfed5c4ff`.
- [Artifact metadata](artifact.json): 792 files, 21 distinct run IDs / 22 component archives. `evidence-index.json` inside binds every raw file hash to its archived context. Configuration provenance shares the full run ID and is not another test execution.
- Current full/default/CI reports, native plans/counters, scope/configuration/versions, cleanup proof and eleven library build-output hashes are included. No library binaries are published.
- Earlier prototype/failed/source-changed attempts and the intentionally interrupted db31975 full remain separate. Selected diagnostics are full NOT_RUN. No historical offline assertion count becomes current JUnit evidence.
- All-future/gathered-stat probes used 1859 versus 1710 logical gets. Cursor reload continuity makes one per-cursor execution sum ambiguous: use completed selector callbacks and stable native-session deltas. The reviewed derivative retains the original hash and raw snapshots; no uniform/constant-work or production capacity claim.
- Current million-row workload completed 200 measured reconciliations (210 including warmup), not one million. DDL owned-fixture teardown 9045 ms is not a row-DELETE benchmark. Actual plans/resources/history and counter intervals remain explicit in QM/raw JSON.
- Generated credential shapes/sensitive XML properties were scanned in UTF-8/UTF-16/nested ZIP inputs with zero findings. Current owned container/volume independently absent; caches retained, unrelated resources unchanged.

The immutable [4f44080 archive](../2026-10-20/raw-evidence.zip) and [1a9187c archive](../2026-10-21-atomicity-queue/raw-evidence.zip) retain their original hashes and evidence. No main change, merge or package publication.
