# Named-target gate mapping and resource-blocked full attempt

Tested clean source `f9f6551978d08fc7bc2db739aa73dc99268e2490`, tree
`2fa819434c813e07030115a78110ae34cd56212f`, on the existing PR #1 review branch.
Named target: `oracle26-nowait-ledger-first-v1` / named configuration only.

- Default run `102a74ee-b161-4127-9b0b-12003b765e90`: Maven0 / qualification0 /
  collector0;156 JUnit in34 XML suites,84 Python guards, no failures/errors/skips.
  Recorded execution audit:102 plugin goals, none expensive. Standalone86 core /
  43 trigger assertions are separate from JUnit.
- Earlier source `b575f45` default attempt `f3658e46-274d-4e68-a387-2eb1cb44f14c`:
  Maven1 / collector0;83 Python checks,one failed source guard. JUnit NOT_RUN.
  The guard was updated to require the same heap/direct caps for characterization.
- Full admission `aea36634-c097-4d47-9f28-a42fcfec175a`: BLOCKED.
  Host available13.139/13.174/13.139GiB below unchanged18GiB; Docker free19.976GiB,
  disk C85.234/D1069.963GiB. Oracle budget6GiB/4CPU unchanged, runtime budget8GiB,
  reserve4GiB. Gate record exit2; the enclosing tool's PowerShell exit was1.
  Full wrapper and Maven full exits are null/NOT_RUN, not failed suite exits.
  No admission retry, provisioning, credentials, new container or volume occurred.
- All77 mandatory Oracle methods /16 suites, PIT, Gatling, capacity and current
  driver characterization: NOT_RUN. No new Oracle runtime receipts or completed
  reconciliation measurement exist for this attempt. No million-row seed occurred.
- Independent plain-UPDATE cancellation remains historically FAILED, source
  `2c39b75` / report `43040e7`; the original method body/assertions are hash-guarded.
  The accepted mechanism applicability change is not a passing rerun.
- Production NO-GO. Full qualification, failed-cooldown publication/recovery,
  security/operations and other applicable parent release gates remain open.

[`execution-analysis.json`](execution-analysis.json) separates each source/attempt.
[`raw-evidence.zip`](raw-evidence.zip) contains original logs,34 current default XML
suites, run markers/manifests and run-scoped QM, failed pre-suite diagnostics,
resource samples, blocked QM without a run marker, source contracts, supervisors
and preservation fingerprints. The earlier default is archived before invalidating
its marker; the later blocked summary imports no earlier JUnit/PIT/Oracle results.
The blocked summary's default NOT_RUN refers to that unexecuted full attempt only;
the separately identified successful default remains PASS.

ZIP SHA-256 `55ef3c20e55550393879a58a614832812d70ffc79def03b43a385c379b4d716d`,
280468 bytes /117 entries. CRC and every input hash verified.
[Package verification](package-verification.json) records the bounded nonsecret
scan; it is not a universal secret audit. All12 earlier raw evidence archives
were hash-verified unchanged against report `5819b80`. Production source files
are unchanged from that baseline. No resources needed removal, and no unrelated
resource, global setting, main branch, merge or package publication changed.
