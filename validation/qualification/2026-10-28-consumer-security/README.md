# Final-source secured-consumer execution — 2026-10-28

**Default and cheap consumers PASS. Combined Oracle security qualification BLOCKED;
MVC and WebFlux Oracle journeys NOT_RUN. Production NO-GO.**

No fixture, library, security configuration or assertion was changed during this
attempt. The existing exact stack-specific status checks, CSRF/method authorization,
positive controls and full acceptance matrix remain intact. Historical 401/403
dispatcher behavior remains unexplained; this blocked attempt adds no runtime observation.

## Frozen source and immutable artifact set

- Repository `Tobia5/model-sync-starter`, PR #1, branch `review/layered-refactor-qm-20261020`.
- Fixture source **`5dd038138a9201f7f4423c5ebe8e1d604f4a4ae4`**, tree **`edcd6c30fc02fd1a2f1136fc9e4aec41889c2292`**, clean before/after execution.
  The fixture/tooling is unchanged from 8df8c94; intervening commit 5dd0381 contains reports.
- Artifact producer **551427bed4d021339fcc32fef8f633232ff8deb8**, tree
  `892daec99e12738607a9bfe736d8e98ee8218d0c`, original packaging run
  `b3db2e0f-e56a-420f-b379-0d1e73f15eb0`.
- All **39 staged files** and the copies in both new consumer repositories match
  the original hashes. Original manifest SHA-256:
  `247c546b54c49b029d77de0420b09aead4b20f2d9bcc8e56cb48c9d032c3c166`.
  No staged artifact was rebuilt or replaced. The required producer default build
  does not install/deploy into or alter this original artifact set.
- Fresh consumer sources were staged from this frozen checkout into
  `../qualification-evidence/security-consumers-20261028-r1/consumers-r1/{mvc,webflux}`.
  Their source hashes, separate repository paths, artifact coordinates, dependency
  trees/classpaths and exact commands are in [artifact-manifest.json](artifact-manifest.json)
  and the raw archive. No old consumer source/target directory was reused.
  Only public dependency cache files were copied from the previous task caches;
  `io/modelsync` was explicitly excluded from that copy.

## Execution record

| Stage | Identity / measured count | Original exit and result |
|---|---|---|
| Matching default | `6e85c4fe-3bc4-40b7-ae3e-db0893a8b9cc`; 222 JUnit / 41 XML / 116 Python | Build 0, qualification 0, collection 0; PASS |
| Standalone MVC preparation | 5 cheap authorization methods, no failures/errors/skips | Maven preparation 0, profile dependency resolution 0; PASS |
| Standalone WebFlux preparation | 6 cheap authorization methods, no failures/errors/skips | Maven preparation 0, profile dependency resolution 0; PASS |
| Core / trigger / recorded default audit | 86 / 43 contract assertions, separate from JUnit | 0 / 0 / 0; PASS |
| Consumer infrastructure attempt | `a85f571c-7cde-4b9e-979e-694155ce34b9` | Wrapper **2**; BLOCKED before provisioning |
| MVC Oracle journey | No current run ID, receipt, Failsafe XML or phase results | **NOT_RUN**, Maven exit absent |
| WebFlux Oracle journey | No current run ID, receipt, Failsafe XML or phase results | **NOT_RUN**, Maven exit absent |
| Diagnostic collection / validation | Fresh failed-admission receipt, source checks and independent absence checks | 0 / 0; does not override wrapper exit 2 |

Commands (exact argument arrays, working directories and timestamps are archived):

```text
mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml -Dmaven.repo.local=<task-owned-producer-cache> clean verify
python scripts/consumer_security.py stage <new-external-workspace> --artifacts <original-551427b-workspace>
python scripts/consumer_security.py prepare <new-external-workspace>
bash scripts/test-core.sh
bash scripts/test-triggers.sh
python scripts/check-default-log.py <current-default-log>
python scripts/full-oracle-container.py --consumer-diagnostic <new-external-workspace>
```

The staging/preparation commands were invoked through the same script functions by
the retained external supervisors. Each consumer preparation separately runs Maven
`clean test` plus dependency-tree/classpath/plugin resolution. The additional
`-Psecurity-oracle dependency:go-offline` resolves the opt-in plugin without executing
Oracle. Intended offline runtime `-Psecurity-oracle verify` commands were **not reached**.

Invocation-only settings remained `MODELSYNC_ORACLE_CONTAINER=EXPLICIT_DISPOSABLE`,
`MODELSYNC_FULL_ENV_ACK=DISPOSABLE_NONPRODUCTION` and
`MODELSYNC_MAVEN_SETTINGS=.mvn/qualification-central.settings.xml`.
Settings SHA-256 `ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`.
No user/global settings or normal Maven cache changes. Java 21.0.12.1, Maven 3.9.9,
Python 3.12.10; Boot 4.1.1 / BOM-pinned Spring Security 7.1.1 remain unchanged.
No current Oracle/JDBC/Hibernate runtime version receipt exists because execution
did not start; prior runtime receipts remain attributed to their original runs.

One external supervisor launch initially exited 1 on a copied UTF-8 BOM decoded
with the locale encoding, before Maven ran. Its failure and bytes are retained.
Explicit UTF-8 decoding corrected only that task-local supervisor; the subsequent
actual default build/exits above are separate, and repository source was unchanged.

## One bounded admission and cleanup

Resource receipt **`38e2f1ed-ccd9-4bc0-9de0-c8be034f1d9d`** was collected after default build,
collection and both consumer preparations finished:

| Resource | Observation | Requirement / decision |
|---|---|---|
| Host available RAM | **6.246 / 6.251 / 6.259 GiB** | At least **12 GiB**; FAILED admission criterion |
| Docker available capacity | 19.441 GiB, 8 CPUs | Existing 6 GiB / 4 CPU Oracle allocation; PASS |
| C / D free disk | 59.651 / 1066.587 GiB | At least 20 GiB each; PASS |

The separate full budget remains **18 GiB**. No threshold reduction, additional
admission attempt, global setting change or unrelated workload termination occurred.
The pinned Oracle image and 6 GiB / 4 CPU limits were retained in configuration:
`ghcr.io/gvenzl/oracle-free:23.26.3-slim@sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`.
No image/container/schema/credential provisioning or Oracle-journey JVM startup occurred.
Generated per-run credentials, separate runtime/migration schemas and loopback-only
ports remain required for an admitted execution.

Independent Docker container and volume queries filtered by the exact attempt
ownership label both exited 0 with empty results. No test-owned runtime resources
remain. New external source/build directories, caches and evidence are intentionally
retained; unrelated resources were not modified.

## Acceptance status and next prerequisite

All required live outcomes are **NOT_RUN for this source/attempt**: four committed
reconciliations per host, same subject in two tenants, all 30 denials per host,
expected/actual HTTP status and challenge observations, unchanged protected side
effects, direct-facade defenses, 32 concurrent WebFlux identities, authenticated/
CSRF-valid positive controls, rollback/enlistment, disabled mode and physical shutdown.
Cheap tests are not substitutes for these Oracle journeys. Earlier WebFlux passes
are historical evidence only; the combined milestone cannot be marked PASS.

The next prerequisite is **at least 12 GiB available host memory at the existing
post-preparation admission check**, with the Docker/disk checks also satisfied.
Any later attempt needs fresh source-matched default/consumer evidence; no marker
or report from this blocked attempt may be reused as runtime proof. An unexpected
status mismatch must retain bounded nonsecret handler/dispatcher evidence before
proposing a fixture change. No arbitrary-4xx acceptance is permitted.

Accepted full a0f46d7/report 8d07aa7, artifact consumers 551427b/report 50c9370,
security fixture 8df8c94/report 5dd0381 and all **91 prior qualification files**
were checked unchanged. The prior QM content is preserved. No R06.1/R06.3 runtime
leaf newly closes. Real identity-provider integration, revocation, provider transport,
retention, uncertainty, operations and distribution remain separate open gates.
No producer full, PIT, Gatling, million-row workload, merge, main/workflow change,
package publication or production approval occurred.

[Results](results.json), [artifact hashes](artifact-manifest.json),
[raw evidence](raw-evidence.zip), [raw inventory](raw-manifest.json),
[checksums](SHA256SUMS.txt). Run-scoped report and evidence copies are under
`target/qm/consumer-security/a85f571c-7cde-4b9e-979e-694155ce34b9/`.
