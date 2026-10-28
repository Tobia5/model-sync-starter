# Independent consumer authorization and tenant isolation — 2026-10-26

**Current default/cheap security PASS; Oracle admission BLOCKED; current Oracle journeys NOT_RUN.**
The earlier WebFlux journeys passed, within two overall FAILED attempts. Final-source
Oracle verification of both stacks remains open. Production remains **NO-GO**.

## Source and artifact identity

| Item | Identity and result |
|---|---|
| Frozen security fixture | `8df8c949b8ddd570cf723e7f7ab522c9aab2e3f2`, tree `6bf4d2531e1c42d067f4db99ad844f87f4a8430e`, clean before/after |
| Matching default run | `f87f69d3-16fa-466b-b2e9-25031ab0b0b7`; 222 JUnit tests / 41 XML files, 116 Python checks; build/qualification/collection exits 0 |
| Independent cheap consumers | MVC 5, WebFlux 6 JUnit methods; no failures/errors/skips; both preparation exits 0 |
| Final infrastructure attempt | `47b66c3c-6d1e-425e-bedb-ebd7d1438ec2`; wrapper exit **2**, resource admission BLOCKED |
| Admission receipt | `a43eccc1-5616-4b8b-9e12-ecd52f3fa124`; host 9.458–9.699 GiB against unchanged **12 GiB** |
| Final consumer Oracle runs | **NOT_RUN**; Maven exits absent, no runtime run ID or receipts created |
| Artifact producer | `551427bed4d021339fcc32fef8f633232ff8deb8`, tree `892daec99e12738607a9bfe736d8e98ee8218d0c` |
| Original packaging run | `b3db2e0f-e56a-420f-b379-0d1e73f15eb0`; original manifest SHA-256 `247c546b54c49b029d77de0420b09aead4b20f2d9bcc8e56cb48c9d032c3c166` |

All **39 staged artifacts** retain their original hashes. The artifact manifest lists
coordinates, attachment hashes, standalone source hashes, commands and dependency
trees/classpath hashes. Model Sync runtime/library/platform source is unchanged from
551427b, checked before staging and execution. Consumer dependency resolution uses
separate task-owned repositories outside the producer reactor. Runtime CodeSource
and JAR-hash receipts exist for the two earlier executed attempts. No producer
classes/source directories, systemPath, IDE resolution or sample internals substitute
for the staged artifacts. Public dependency cache copies are recorded separately.

The accepted full baseline a0f46d7/report 8d07aa7, independent consumer baseline
551427b/report 50c9370 and all 22 prior raw archives remain unchanged. R10.1, R10.2
and R13.2 remain completed within their existing scopes. No packaging qualification,
producer full, PIT, Gatling, million-row seed, merge or publication was repeated.

## Commands, exits and environment

Approved project settings: `.mvn/qualification-central.settings.xml`, SHA-256
`ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`.
The user's settings/cache were not changed. Exact command arrays, working directories
and start/end times are in [results](results.json) and the raw archive.

1. Producer `mvnw.cmd -B -ntp -s <approved-settings> -Dmaven.repo.local=<task-owned-m2> clean verify`:
   exit 0; independent QM qualification/collection exits 0. Core/trigger contracts
   (86/43 assertions), architecture/docs checks and default execution audit passed.
   Assertion counts are separate from JUnit counts.
2. `python scripts/consumer_security.py stage <workspace> --artifacts <original-staged-workspace>`
   then `prepare <workspace>`: two separate Maven `clean test` invocations with dependency
   tree/classpath capture and public dependency/plugin resolution; exits 0/0.
   A separate `-Psecurity-oracle dependency:go-offline` resolves Failsafe without
   running Oracle. Five MVC / six WebFlux cheap tests passed on the frozen source.
3. Invocation-only disposable acknowledgments and approved settings, then
   `python scripts/full-oracle-container.py --consumer-diagnostic <workspace>`:
   original exit **2**. The intended separate offline `-Psecurity-oracle verify`
   invocations were **not started**. No prior runtime reports were attributed to it.

Java 21.0.12.1, Maven 3.9.9, Boot 4.1.1 and BOM-resolved Spring Security **7.1.1**.
Earlier runtime receipts verify Oracle/JDBC 23.26.3 and Hibernate 7.4.5.Final.
Pinned image:
`ghcr.io/gvenzl/oracle-free:23.26.3-slim@sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`.
The existing 6 GiB / 4 CPU Oracle cap, loopback ports, generated credentials,
separate runtime/migration schemas and 512 MiB heap / 128 MiB direct-memory JVM
caps remain unchanged. Each enabled consumer uses pool 4, two worker/persistence/
owner threads, bounded queues and the previous pacing/timeouts. Full budget stays 18 GiB.

## Application-owned security boundary

The standalone applications use Spring Security's actual MVC/WebFlux filter chains,
HTTP Basic authentication, method authorization and CSRF protection. Test-only
in-memory identities receive generated per-JVM passwords; no real identities,
external provider, custom token protocol or core security dependency was added.
CSRF, BREACH handling and security headers remain enabled. Live clients use the
framework token/session; cheap tests use Spring Security's MockMvc/WebTestClient support.

The application policy checks exact operation/tenant/binding/subject authority
before calling the gateway or publishing an event. Read, refresh and event grants
are separate; unknown scope and missing authority fail closed. Policy lookup does
not read protected model payloads. A proxied facade also protects direct service
and event-ingress calls. Raw `SyncGateway`, internal adapter and event publisher
remain **trusted in-process capabilities**; these tests do not sandbox hostile
code with those references. External ingress must use the authorized facade.

The reactive facade checks Reactor SecurityContext and carries only a detached
principal name into blocking work on `modelSyncPersistenceScheduler`. The durable
worker has an explicit known-ID service scope for alpha/beta and item; it checks
that no request principal was inherited. Queuing does not carry a live request
SecurityContext or grant. Revoking already accepted intent is not qualified here.

## Executed Oracle evidence — preserve each attempt's status

| Source / run | Overall; original wrapper/MVC/WebFlux exits | MVC observed scope | WebFlux observed scope |
|---|---|---|---|
| f206718 / `2b8e502f-31ca-4ded-bccb-93911f17bb11` | **FAILED**; 1/1/0 | Two initial committed outcomes; one completed negative case; failed expected 403 versus actual 401 for anonymous POST; later phases NOT_RUN | **PASS**: 30 negative cases, four owner calls / four committed reconciliations, 32 concurrent HTTP identity checks |
| 8e36028 / `143adeff-77ef-4864-ad9c-23ab46534c6c` | **FAILED**; 1/1/0 | Two initial committed outcomes; three captured negative snapshots with unchanged side effects; failed expected 403 versus actual 401 for missing CSRF; later phases NOT_RUN | **PASS**: all 30 negatives, four owner calls / four committed reconciliations, 32 concurrent HTTP identity checks |
| 8df8c94 / current attempt | **BLOCKED**; 2/absent/absent | NOT_RUN | NOT_RUN |

Both executed WebFlux hosts committed alpha-A/beta-A for the same subject `shared`
at revision 0 / generation 1, then event-alpha-B and refresh-beta-B at revision 1 /
generation 2. Fresh connections confirmed paired projection/state, matching
validators, APPLIED outcomes and IDLE work. Facade counters were 35 reads, three
refresh enqueues and one event; one actual event publication, four physical owner
HTTP calls and four committed reconciliations were counted separately.

Each of the 30 negative cases compared before/after protected gateway reads,
JPA mapping reads, enqueue calls, actual event publication, physical owner calls
and committed projection/state/work rows (including generations and freshness
fields). All captured pairs in the executed attempts are unchanged. Policy lookup
and idle selector activity are separate from protected model-payload access.

The complete WebFlux matrix covers unauthenticated requests, missing CSRF,
cross-tenant/unknown-tenant/wrong-binding/wrong-subject access for all three
operations, missing grants, read-only versus refresh/event permissions, unknown
operation, and direct anonymous/insufficient/cross-tenant facade calls. All run
against fresh protected values. Its 32 concurrent HTTP reads returned the right
tenant's B value across the real persistence scheduler, without owner/enqueue/
event/state changes. Qualified mapping/effective policies, five auto-configurations,
DatabaseClock replacement, datasource/JpaTransactionManager enlistment, trusted raw
JPA/JDBC rollback, disabled mode, pools and physical shutdown checks passed.
The rollback control does not claim cross-thread caller-transaction propagation.

## Fixture corrections and remaining execution gap

Initial cheap dirty-source probes rooted at 50c9370 failed one method in each
stack because the expected missing-context exception was too narrow. The corrected
helper accepts only Spring Security denial types or the bounded MVC SpEL cause
chain containing AuthenticationCredentialsNotFoundException. Unrelated errors fail.
The next cheap attempt passed both stacks.

The first live servlet run returned 401 for anonymous POST where MockMvc had
returned 403. A retained cheap probe rooted at f206718 failed when it incorrectly
applied the live 401 to MockMvc. The second live run then exposed the same 401/403
expectation mismatch for missing CSRF. These are measured response differences;
their framework dispatch cause was not diagnosed. No authorization bypass or
runtime-library defect was observed, and no security protection was disabled.

Current source 8df8c94 records expected/actual status and authentication-challenge
presence and collects the complete HTTP status matrix before aggregate status
assertions. It still fails immediately on protected side effects. Live MVC expects
401 for the observed anonymous/CSRF cases; WebFlux and MockMvc retain their own
measured expectations. This correction is **cheap-test verified, not yet Oracle
verified**. Both final-source Oracle journeys remain required; prior WebFlux PASS
is not substituted for current execution. Failed attempts and partial phases
remain immutable in the raw archive, including dirty-source identities.

## Resource admission and owned cleanup

Final host samples: **9.699, 9.458, 9.656 GiB**, below 12 GiB. Docker capacity:
19.517 GiB available / 8 CPUs; free disks C/D: 62.914 / 1066.880 GiB. Only the
host-memory criterion failed. No bounded-admission retry, global configuration
change or interference with unrelated workloads followed.

The final attempt created no container, volume or consumer JVM. Independent
Docker queries for its exact ownership label confirmed no resources. Earlier owned
containers `msq-c4a7d351f68c`, `msq-bbec4ca1a2c5` and their `-data` volumes are absent;
both attempts' consumer JVMs are absent. WebFlux pools/threads/physical operations
and fixture cleanup passed; MVC finally-cleanup and process/container absence are
recorded separately from its unexecuted normal shutdown assertions. Evidence,
staged artifacts and dedicated local caches intentionally remain.

## Acceptance and limits

R06.1/R06.3 close only the demonstrated cheap facade/filter-chain checks and the
source-bound earlier WebFlux scenarios. Final two-stack Oracle security verification
is **open, blocked on at least 12 GiB available host memory**. This is not a complete
security audit. Real identity-provider integration, policy/revocation/tenant lifecycle,
other APIs/visits/replay, provider TLS/SSRF/secrets, retention, uncertain outcomes,
operations, capacity and remote distribution remain separate open requirements.
Historical independent cancellation remains FAILED; its current characterization
and producer full are NOT_RUN. No production acceptance is implied.

Raw artifacts: [archive](raw-evidence.zip), [file inventory](raw-manifest.json),
[checksums](SHA256SUMS.txt), [artifact provenance](artifact-manifest.json),
[machine-readable results](results.json). Run-scoped copies are retained under
`target/qm/consumer-security/47b66c3c-6d1e-425e-bedb-ebd7d1438ec2/`.
