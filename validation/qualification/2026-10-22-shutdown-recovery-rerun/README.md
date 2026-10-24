# Full qualification after headroom restoration

**Default PASS · Full FAILED · Production NO-GO.**

Tested source `08bd1906e54d6a182a1a7d2c9473fc255f7038fa`, tree
`a5f5e717c9fcd3331c139558508c312aff901d60`, clean empty change digest.
See the current section of [root QM](../../../QM.md) for commands, versions,
fault outcomes, resource bounds, scope limitations and remaining release gates.

| Run | Exact identity | Result |
|---|---|---|
| Fresh default | `2bb7a10f-29a9-4b30-9313-48aa2d1960d9` | exit0/65.559s;117 JUnit/61 Python;102 recorded goals, none expensive |
| Complete full | `e88fc1de-755e-470c-a7f7-15c3c7a4f13d` | Maven1/wrapper1/supervisor1/collector0;7698.963s;119 Surefire/45 Oracle, one Oracle failure, zero errors/skips |
| Infrastructure attempt | `3dabb75d-761d-427a-88f7-5126042f4b90` | pinned image,6GiB/4CPU,loopback,generated credentials/separate schemas; cleanup independently confirmed |

- [Raw evidence ZIP](raw-evidence.zip): SHA256 `7ce24ee9711e023d5000f0d3ff76b5bad0ad2d71bc4417cfa1b8472631d9db34`,7,102,974 bytes/1,972 files/25 source-bound components.
- [Per-file index](artifact-index.json) and [packaging/disclosure receipt](artifact.json). Every declared attempt hash and ZIP CRC passed; zero bounded secret-pattern findings. No JAR/class binaries; eleven freshly built library outputs are hashed only.
- Current full raw directory: `attempts/full-e7a832ce-9b6e-481d-8c94-d01aee836ffe/`. Its343 fresh files include the original failed log/exit, run marker, run-scoped `target/qm/QM.md`/machine evidence, Surefire/Failsafe XML, fault/process records, PIT XML/HTML, six Gatling reports, Oracle plans/counters/waits/phase costs, worker measurements and independent cleanup.
- Current default: `attempts/default-f42cd241-725a-4499-a348-e90d25af74fd/`. `session/default-resume-08bd190-summary.json` and `session/default-execution-audit-resume.log` retain its117 tests and102-goal separation audit. `session/offline-resume-08bd190.*` records86 core/43 trigger offline assertions separately from JUnit counts.
- Exact build configuration/effective POMs/resolved classpaths/eleven JAR hashes: `attempts/build-provenance-e88fc1de-755e-470c-a7f7-15c3c7a4f13d/`.
- Infrastructure: `session/capacity-before-full-08bd190.json`, `session/full-container-observation-08bd190.json` and `session/resources-resume-08bd190.json` retain admission, caps/loopback binding and coarse whole-container snapshots. These are not native selector/session/cursor deltas.
- Original binary Gatling logs and ICO files retain their bytes. Packagingv2's decoder failure andv3's narrow pinned3.13.1-header handling are recorded; unknown formats still fail. Qualification uses the unchanged XML/JSON/assertion evidence, not text interpretation of binary logs.
- Older failed full, scoped diagnostics, resource-blocked attempt and prior default/CI evidence each retain their own source/run/status. No historical counts are added to this run. All five preceding ZIPs remain unchanged.

**Sole full failure:** `OracleIoIT.realStatementLockWaitCancelsAndRollsBack`.
Effective query timeout2s, physical Oracle NIO read still active at5017ms;
`Future.get(5s)` timed out instead of completing with the required execution
failure/ORA-01013. The2s/5s gate was not weakened. Eleven other I/O cases passed.

All six independent-JVM crash cases, seven starter atomicity cases and ten actual
worker phases passed. PIT160/162; six semantic Gatling scenarios/367 requests/zero
HTTP errors. Leases harness96 completions from200 accepted requests. Million-row
seed:200 measured reconciliations/210 with warmup, not a million completed syncs.

Wrapper cleanup and independent checks at2026-10-22T14:19:00.767065Z confirmed
`msq-3de5da8e40a9` and its matching`-data` volume absent. All eight session-owned
container/volume pairs have independent absence receipts; no unrelated resource
was changed. Memory admission was cleared for this attempt; statement cancellation
and passing full qualification remain open. No main change, merge or publication.
