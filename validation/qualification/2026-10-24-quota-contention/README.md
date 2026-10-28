# Shared-quota contention evidence - 2026-10-24

Source **`4057535359400242c3a20d69f7ad06197caee5e6`**, tree
**`4bfbfaea40f5413fe3bd0067b7f9b9047bfcfb1e`**.

- Default command: `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify`.
  Run `50f38395-84ab-4b90-973d-6c7a6c154280`, exit0/129.017s, collector0;
  149 JUnit/32 XML suites,76 Python guards,0 failures/errors/skips.
- Focused command: `python scripts/full-oracle-container.py --quota-diagnostic`.
  Run `35ead9f5-f6af-4c49-a323-4ad149737b85`, attempt
  `03c255b7-79ec-43a9-81cf-de94e8b25a90`, preflight/launcher/wrapper0,
  144.871s supervision/132.08s infrastructure. Six found/started/succeeded,
  zero failures/skips/container failures,12471ms JUnit launcher duration.
- Six cases: default compatible; held acquire3 refusals; held cooldown3 refusals;
  coordinator admission refusal/recovery; owner-read cooldown refusal/recovery;
  existing registration consistency. Eight Oracle54 refusals,8-30ms final client/
  transaction return, no outstanding lock wait with blockers held, guard rollback,
  healthy/disposed resources. Three synthetic owner reads/two completed syncs.
- Strict detailed evidence validation passed. Only owned container/volume
  `msq-5e4a26e2d97b`/`msq-5e4a26e2d97b-data` removed, independently absent.
- Full/PIT/Gatling/large seed/old prototype/A-B-C NOT_RUN. Original cancellation
  failure unchanged; first inserts and other applicable requirements remain open.
  Prior work/JPA acceptance is separate from quota regression/release acceptance.

`execution-analysis.json` contains original case results/session snapshots/timings,
source/run identity, commands/exits and cleanup. `raw-evidence.zip` contains both
run archives, original/normalized logs, XML/JSON, per-run manifests/default QM,
capacity checks, validation/cleanup/preservation receipts and local supervisors.
Preliminary dirty guard and packaging-parser failures have bounded summaries only;
they are not substituted for source-bound runs or hidden as successful attempts.

Archive SHA-256: `e72ae7b9ccecc0c4757ce6686529bf51be92f2a2868bbffd2f01eb4439819821`; 183465 bytes, 102 files.
CRC and input hashes verified. Bounded nonsecret scans found0; not a universal audit.
The inner packaged-files-sha256.json verifies every input. All earlier ZIPs and
original failure/prototype/integration/atomicity test sources were checked unchanged.
See [QM](../../../QM.md), [coverage](../../../docs/quality/LOCK-POLICY-COVERAGE.md)
and [accepted decision](../../../docs/quality/LOCK-POLICY-DECISION.md).
