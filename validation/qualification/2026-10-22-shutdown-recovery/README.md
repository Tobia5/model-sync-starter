# Shutdown / waits / process-recovery evidence

**Default PASS · completed full FAILED · final-source full BLOCKED · production NO-GO.**

See the current section of [root QM](../../../QM.md) for commands, versions,
scope, faults, outcomes, durations, limitations and unchanged historical evidence.

| Evidence | Exact source | Run / result |
|---|---|---|
| Final local default | `8830c3f6d2ac8483c35309e747e896131c64fcea`, tree `10cb9835a8f925f9e45a2a437d9f637ee5668ca2` | `47311f17-5b2c-409b-ab19-8b9340fc8af5`,117 JUnit/61 Python, exit0 |
| Completed full | `3564410ad8e884509cecb0e530a8e3c16473eb29`, tree `d0af02e4117463eae97a25108b537eb9650fd577` | `d0500d1e-030b-425e-9b49-a35e214f7598`,119 Surefire/45 Oracle,2 Oracle failures, exit1 |
| Worker diagnostic | `3564410` plus digest `5e5ba7f20198dbf780748b619e42f67bef2708d827b5802bff42bc12df824da5` | `778da5e2-47b2-430d-840f-8b7b15b5ff6b`, ten phases PASS; not full |
| Final full preflight | `8830c3f`, tree above | `2b64d09c-a58c-4534-b7ff-668539a5f807`, BLOCKED by unchanged18GiB host-memory budget; full NOT_RUN; no run ID |

- [Raw evidence ZIP](raw-evidence.zip): SHA256 `8c236668f6456fec19c919791c970ab292abe493f9172fd6724bd5f9462db49c`,4,432,245 bytes,1,479 files,22 components.
- [Per-file SHA256 index](artifact-index.json) and [packaging/disclosure receipt](artifact.json). CRC and each archived attempt's declared file hashes passed. No JAR/class outputs; eleven library outputs are hashed only. Zero bounded secret-pattern findings; six generated ICO assets retain their original bytes.
- [Final read-only resource recheck](final-resource-recheck.json), recorded after ZIP packaging, still found16.644GiB available at10:28:38Z versus the unchanged18GiB planning budget. It is a separate resource receipt, not a test run or a modification of the archived failed/full evidence.
- Inside the ZIP, `attempts/full-40ae3c64-186c-4716-ab1f-06558fc705c7/` contains original full logs, source/run marker, evidence/QM, Surefire/Failsafe XML, six crash checkpoint/process records, twelve I/O case records, PIT XML/HTML, six Gatling reports, Oracle scale/comparison/workload/cursor evidence and independent cleanup.
- `attempts/diagnostic-*` and `attempts/default-*` retain each development attempt's own identity/status. Earlier failing guards, compile attempts and diagnostics are not counted in current results. Initial direct logs without a captured dirty-source digest are labelled unqualified.
- `attempts/build-provenance-d0500d1e-030b-425e-9b49-a35e214f7598/` contains resolved versions, effective POMs, settings/configuration hashes and eleven freshly built library hashes from the FAILED full. These are not release artifacts.
- `session/default-8830c3f-summary.json`, `session/final-full-blocked.json`, `session/capacity-before-full-8830c3f.json` and `session/offline-8830c3f.*` retain final default, resource refusal and offline contracts separately. The blocked attempt created no resources and borrows no earlier suite results.
- [Default CI39761845131](https://github.com/Tobia5/model-sync-starter/actions/runs/39761845131) tested merge source `9f18eb58dcbb1184355dc9358a226593d5ee3bd4`, whose tree equals final8830c3f. Its QM artifact12418985741 and API source/tree verification are retained separately; full CI was NOT_RUN.

Completed full failures were independent JDBC statement cancellation (effective2s,
still physically reading after5001ms against5s gate) and the actual-worker160-row
backlog20s timeout. The later targeted worker diagnostic passed, preserving both
results and all gates. Six process-crash cases and all seven atomicity cases
passed. PIT160/162; six semantic Gatling scenarios/367 HTTP requests/zero errors.
One million rows were seeded;200 measured reconciliations/210 with warmup completed.

All seven session-owned containers and volumes were independently confirmed
absent. No unrelated resource was stopped or removed. Prior accepted baseline
archives and reports are unchanged. A configured suite result alone never
establishes production readiness; final-source full remains blocked and parent
release gates remain open.
