# Independent local artifact consumption — 2026-10-26

**Local packaging/default and both independent consumers: PASS. Production: NO-GO.**
No producer full rerun, remote deployment/publication, main change or merge occurred.

## Source and commands

- Frozen source `551427bed4d021339fcc32fef8f633232ff8deb8`; tree `892daec99e12738607a9bfe736d8e98ee8218d0c`; clean before/after execution.
- Packaging/default run `b3db2e0f-e56a-420f-b379-0d1e73f15eb0`, supervisor
  `63d37632-2b64-49ca-9317-ed3488da3a03`: build/qualification/collection exits0.
- Exact commands/cwd/settings hashes are in `raw-evidence.zip` and `results.json`.
  Producer command: `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml
  -Dmaven.repo.local=<task-owned-m2> -Plibrary-artifacts clean install`.
  This executes the normal verify lifecycle plus sources/Javadoc and local installation.
  **222 JUnit/41 XML**,0 failures/errors/skips; **112 Python guards**;
  architecture/docs/lifecycle checks PASS. Separate core86/trigger43 assertions
  are not extra JUnit counts; default-log audit147 goals, no expensive goals.
- Stage and prepare commands: `python scripts/consumer_artifacts.py stage <external-workspace>
  --repository <task-owned-m2>` then `python scripts/consumer_artifacts.py prepare <external-workspace>`.
- Consumer run `ca8ae33e-62af-4cc5-935d-cc691de61f60`: explicit
  `python scripts/full-oracle-container.py --consumer-diagnostic <external-workspace>`;
  wrapper/diagnostic/receipt validation exit0. Each mode used a separate Maven
  `-s <approved-settings> -Dmaven.repo.local=<mode-cache> -o verify` invocation
  outside the producer reactor, original exits0/0. Public dependencies were
  prepared before Oracle; each cache began empty except the exact staged artifacts.

## Artifacts and isolation

39 hashed files:12 POMs (parent+11 libraries/starters),11 main JARs,
16 source/Javadoc attachments for eight modules with Java sources.
The three dependency-only starters have no Java attachments by existing profile design.
[Manifest](artifact-manifest.json) records every path/coordinate/hash/size and consumer
source hashes. [Coordinates](ARTIFACTS.md). Actual staged binaries remain in the
task-owned local workspace; the evidence archive is not a registry upload.
No demo, testkit, architecture or performance artifact was seeded/consumed.
Resolved dependency trees, classpaths and runtime CodeSource JAR locations prove
dedicated-repository resolution. No producer source/classes/systemPath/sample configuration.
Primary and nested final packaging Maven invocations inherited the same approved
public Central settings and task-owned repository. User/global settings unchanged.

## Measured consumer outcomes

| Check | MVC | WebFlux |
|---|---|---|
| JUnit methods / failures / errors / skips |1 /0 /0 /0 (6.587s)|1 /0 /0 /0 (9.529s)|
| Public request, changed request, Spring event |A/rev0/gen1, B/rev1/gen2, C/rev2/gen3|A/rev0/gen1, B/rev1/gen2, C/rev2/gen3|
| Fresh committed state |APPLIED + matching validator + IDLE, each phase|same|
| Physical owner HTTP / committed reconciliations |3 /3|3 /3|
| Provisional enqueue + JPA flush rollback |C/rev2/gen3/IDLE retained|same|
| Discovery / named clock replacement / disabled mode |PASS|PASS|
| Effective policies/enlistment/schema extension |PASS|PASS|
| Persistence / blocking owner threads |servlet or model-sync-jpa / model-sync-owner|model-sync-jpa / model-sync-owner|
| Owned scheduler threads/active owners after close |0 /0|0 /0|
| Enabled and disabled pool closure / own remaining ledger rows |true /0|true /0|

Actual B-phase SQL includes
`select ... from consumer_item ... for update of ie1_0.storage_key nowait`,
followed by version-checked `update consumer_item ... where storage_key=? and local_version=?`.
Raw bounded SQL/phase receipts are retained. The consumer-owned mapping consumed
ORACLE_NOWAIT; actual store/quota/insert fields, shared JpaTransactionManager datasource,
five discovered auto-configurations and DatabaseClock replacement were checked.
Target settings: point/quota/JPA ORACLE_NOWAIT, inserts ORACLE_26_NOWAIT,
cooldown DURABLE/schema extension1. Oracle26ai23.26.3/JDBC23.26.3.0.0,
Hibernate7.4.5.Final, Boot4.1.1/JDK21.0.12.1/Maven3.9.9.
Consumer pool4, two lanes/two persistence+owner threads, bounded queue16,
100ms poll/10ms productive/250ms max idle. The post-context-close shutdown receipt
is DRAINED with physical0/owner0; its0ms is not a measurement of total close latency.

## Admission and cleanup

One bounded admitted observation:20.851–20.863GiB host available against12GiB,
Docker19.531GiB available, C/D free75.018/1068.518GiB. Full budget18GiB unchanged.
Live independent inspect confirmed6GiB/4CPU, pinned image digest, only the new
owned volume, loopback-only binding. Consumer JVMs ran sequentially, each512MiB
heap/128MiB direct cap, with the same parent Maven caps.
Generated per-run credentials and separate MS_TEST_RUNTIME/MS_TEST_MIGRATION schemas;
no production credentials or existing database were used. Wrapper runtime63.64s.
`msq-bbf3eb9e58de` and `msq-bbf3eb9e58de-data` independently absent after cleanup;
consumer PIDs5652/59268 absent. No residual owned runtime resources.
Local staged artifacts/caches/raw reports are intentionally retained for review.

## Preserved attempts and limitations

1. Initial8d07aa7 packaging FAILED1 on a real Javadoc `WorkLease @param until`
   mismatch. Collection0; stale module reports rejected. Corrected to `expiresAt`
   in7fb3469; no runtime behavior change. Later source-bound package/default runs passed.
2. Initial outside-reactor MVC compile at e46cca1 FAILED1 due an incorrect
   FreshnessPolicy import in the consumer fixture; WebFlux NOT_RUN. Corrected
   compile probes passed but were not Oracle evidence.
3. Source643c3e2 consumer preflight BLOCKED2 on locale-formatted memory `20,587`;
   no resources created, both runtime suites NOT_RUN. JSON numeric observation and
   cheap regression fixed this in551427b. The new admitted run is separately identified.
4. CLI workspace creation omission produced two guard exits1 before any staging/build;
   creating the required external directory resolved setup. These were not Oracle attempts.

Accepted full baseline remains sourcea0f46d7/report8d07aa7/run751ea112:
83 Oracle methods/18 suites, PIT167/168, six semantic Gatling scenarios PASS.
**Full/PIT/Gatling/million-row checks are NOT_RUN in this task** and are not
attributed to551427b. All prior raw archives and the original QM suffix are preserved.
The only production-source change is Javadoc, not executable logic.
Historical worker timeout remains FAILED/unexplained; independent cancellation
historical FAILED/current characterization NOT_RUN. Neither was reopened.

This consumer-owned single-table mapping proves this journey/enlistment, not
arbitrary mapping contention/crash qualification. Registry authentication,
publication, version-range support and reproducible release provenance remain
unexecuted/open. Unrecorded response/uncertainty, receipt retention, security,
operations and real provider/capacity acceptance remain open in the
[matrix](../../../docs/quality/ACCEPTANCE-MATRIX.md).
