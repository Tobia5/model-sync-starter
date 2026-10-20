# Production readiness and operations runbook

**Applies to:** the trigger extension against baseline `a555ffaf8681b6dcc2597f16a84bf5b2a45c11d6`.
**Release status:** NOT YET QUALIFIED FOR PRODUCTION. The offline contracts pass; the full Maven/framework lane, Oracle v2 migration and production-like contention/load testing remain open. Production-oriented documentation does not substitute for these gates.

## 1. Release acceptance

Record an accountable owner and evidence location for every gate. A blank owner or evidence field means OPEN, not implicitly accepted.

| Gate | Acceptance evidence | Current status |
|---|---|---|
| Dependency build | `mvn -B -ntp verify` succeeds against approved mirror; record JDK, resolved BOM and driver | Not executed here |
| Dependency/security review | SBOM, vulnerability policy, licenses and verified build provenance | Not performed |
| Framework contracts | Spring conditional wiring, events, scheduler shutdown, Reactor bridge and H2 tests | Test sources supplied; not executed here |
| Transaction atomicity | Real JPA projection + JDBC ledger rollback and commit on selected database | Existing test/source coverage; actual production stack unverified |
| Oracle schema | v2 fresh install, v1 upgrade, interrupted upgrade and recovery rehearsal | Scripts supplied; not executed |
| Oracle locking | Concurrent claims with selected driver, skipped-lock behavior, stale-token rejection, insert races | Existing smoke test is insufficient by itself |
| Rate budget | Multi-pod requests, client retries, 429 cooldown and exact provider quota unit | Must measure on approved test endpoints |
| Scale | Representative population and skew, restart storm, due lag, pool pressure, index plans | No million-row benchmark claimed |
| Application security | Tenant authorization, identity resolver, destination allowlist, payload limits, no PII/secrets in logs | Application responsibility |
| Operational readiness | Alerts, audit requirements, backup/restore, incident ownership, rollback and retention | Runbook supplied; implementation/approval outstanding |

`bash scripts/verify-production.sh` runs the available automated lanes and fails if Maven is absent. It is not a substitute for the Oracle, security and operations gates. The supplied CI workflow runs the offline/static and Maven lanes only; it does not access an Oracle production schema or private credentials.

## 2. Deployment sequence

Provision the reviewed schema with a separate migration identity. Deploy a consistent mapping/binding configuration to all participating pods, initially with both executor flags disabled. Validate boot, datasource/transaction alignment, registered quotas, owner transport timeouts and authenticated endpoint restrictions. Introduce a bounded canary tenant/subject set, then enable either the continuous worker or cron execution and inspect evidence before expanding.

API-only pods may continue serving safe local reads and accepting work while a separate executor deployment drains it. Conversely, stopping admission does not stop existing recurring work. In an incident, identify separately whether to stop external calls, new requests, or both.

Use `spring.jpa.open-in-view=false` and `ddl-auto=validate`. Do not deploy the `demo` profile or its H2/create-drop configuration against external storage. Startup compatibility checks are not a complete schema checksum; migration history and configuration fingerprinting remain part of your deployment controls.

For a headless worker, explicitly set `spring.main.web-application-type=none`. There is no built-in authenticated management HTTP API. Do not expose work tickets, arbitrary tenant IDs or diagnostic projection inspection publicly without host authorization.

## 3. Schema v1 to v2 migration

### What changes

V2 adds the permitted work status `IDLE` and names its status constraint `MS_WORK_STATUS_CK`. Table columns remain the same. Periodic code continues to use WAITING/LEASED/DEAD. ON_DEMAND requires an idle-capable work store and v2 metadata.

Fresh-install scripts must only run in an empty schema. For an existing Oracle v1 schema, review [oracle-v1-to-v2.sql](../model-sync-persistence-jdbc/src/main/resources/model-sync/migration/oracle-v1-to-v2.sql). The script uses `USER_CONSTRAINTS` to match the exact original status predicate, rather than assuming Oracle's generated constraint name or removing arbitrary CHECK constraints.

### Before execution

Take the approved recoverable backup and record table counts, schema version and relevant constraint definitions. Stop **all writers and executors**, including API admissions and other pods. Resolve outstanding LEASED rows through the approved lease-recovery procedure before migration; the script rejects any remaining leased rows. Do not clear tokens blindly. Run the script first in an isolated Oracle clone using the same server/driver family.

The script requires the application schema owner/migration identity and SQL*Plus/SQLcl-compatible execution. It must not be sent as an unreviewed JDBC startup script. A preflight mismatch stops migration for DBA review; do not loosen its predicates to make it pass.

### During execution

The script verifies metadata v1, no active ledger leases, no conflicting target constraint name, and exactly one enabled/validated v1 status constraint. It **adds the broader v2 constraint before dropping the old one**, so an interrupted change does not leave the table without a status constraint. Only after that does it record metadata v2.

Oracle DDL commits implicitly. This is not an atomic transactional migration, and `ROLLBACK` does not undo earlier DDL. A maintenance window and rehearsed recovery are mandatory.

### Interrupted migration recovery

| Observed state | Safe interpretation / action |
|---|---|
| Metadata v1, old constraint only | No schema change completed; diagnose before retry |
| Metadata v1, old and named v2 constraints | New check added; old check still forbids IDLE. Keep all services stopped; DBA reviews and completes the intended drop/version steps |
| Metadata v1, named v2 constraint only | DDL completed but version commit did not; validate predicate and existing data before recording v2 |
| Metadata v2, named v2 constraint enabled/validated | Run smoke and transaction tests before enabling workers |
| Any other combination | Stop; reconcile actual DDL and data with migration evidence, not guessed metadata edits |

The supplied script intentionally refuses an automatic rerun if the named target constraint already exists. This avoids hiding partial migration behind “idempotent” metadata writes.

### After migration and rollback

Verify the exact constraint, version, row counts and no unintended data changes. Run the database integration lane and representative on-demand/event/visit flows. Deploy only the updated binary; the original binary recognizes version1 and is not a supported rollback onto v2.

Prefer disabling workers and rolling forward a fix. Returning to an old binary requires a separate reviewed downgrade, explicit handling of IDLE rows, and a compatible status constraint/version. There is no supplied automatic downgrade. Deleting IDLE work would also delete or orphan necessary evidence and is not an acceptable shortcut.

## 4. Changing recurrence and quota policies

Switching PERIODIC to ON_DEMAND makes already scheduled subjects idle as each completes under the new binding. It does not perform an immediate bulk state rewrite. Switching ON_DEMAND to PERIODIC does not awaken existing IDLE rows; re-enqueue the intended subject population in bounded pages through the API.

Keep all pods on the same policy and mapping during such a transition, or define a controlled canary boundary. Mixed configurations are not automatically rejected by a persisted binding-version registry.

Quota registration checks configured spacing against the persisted value and rejects disagreement. Changing YAML on only one pod is not a valid shared-quota change. Use a controlled rollout and reviewed database/configuration procedure that preserves current cooldown; never reset `next_allowed_ms` simply to get more throughput.

## 5. Observability and safe diagnostics

The following are **required integration metrics**, not a claim that a full Micrometer module exists: queued/due/leased/dead/idle counts; due lag and age-limit violations per binding; owner invocation rate/latency/429; quota deferral; outcome and local-conflict counts; persistence/owner scheduler saturation; connection waits; job stop reasons; and broker replay/inbox rate.

Keep tags low-cardinality. Do not tag metrics with subject IDs or tokens. Use a correlation ID or protected audit trail when record-level diagnosis is needed. Stable error codes may be logged; provider bodies and exception messages may contain sensitive data and should not be logged indiscriminately.

Read-only diagnostic examples (use a restricted operator account and a suitable cadence; aggregate scans over millions of rows are not free):

```sql
SELECT binding_id, work_status, COUNT(*) AS item_count
FROM MS_WORK
GROUP BY binding_id, work_status;

-- :now_ms is a bound epoch-millisecond value from the shared database clock.
SELECT binding_id, MIN(next_at_ms) AS oldest_due_ms, COUNT(*) AS due_count
FROM MS_WORK
WHERE work_status='WAITING' AND next_at_ms <= :now_ms
GROUP BY binding_id;

SELECT binding_id, last_error_code, COUNT(*) AS failures
FROM MS_WORK
WHERE work_status='DEAD'
GROUP BY binding_id, last_error_code;

SELECT quota_group, spacing_ms, next_allowed_ms FROM MS_QUOTA;
```

Use authorized, tenant-scoped point lookups for individual tickets. `MS_STATE` stores last evidence, not a complete audit history. `IDLE` is a scheduling state, not necessarily MATCH; join outcomes when assessing correctness. Freshly validated MISMATCH/OWNER_ABSENT is not a successfully reconciled value.

## 6. Incident procedures

### Provider outage or 429 surge

Preserve rate-limit/cooldown evidence. Stop or reduce execution if the provider requires it; do not raise concurrency or force-request every subject. Check whether other applications share the external quota but not the same ledger. Audit retries inside custom HTTP clients, gateways and MDM lookups: each physical call can consume provider budget.

Passive visits retain cooldown/backoff. Explicit forced requests remain privileged operations and can alter due work; protect their admission from noisy/untrusted sources. If external calls are stopped, expose pending/unavailable state and obey the maximum serving age.

### Dead work or poison event

Inspect the stable error and mapping/source contract in a protected environment. Correct the cause first. Replay a bounded, authorized subject set using `ModelSync.request(tenant, id, priority, true)`; this is an explicit revival of DEAD work. Never let every visit silently reset failures. Where messages replay poison work, quarantine the message/inbox entry under the broker integration's policy.

### Stuck lease or worker crash

Check process health, transport timeout and shared time. A replacement worker can claim expired leases and receives a new token. Do not manually reuse old tokens or commit their responses. If graceful shutdown cannot stop a blocking adapter, allow lease expiry; obsolete completion must still be rejected. Repeated physical reads can occur around crash/timeout and are not an exactly-once guarantee.

### Projection mismatch or source absence

Do not turn on repair blindly. Verify authority for every mapped field, canonicalization/digest version, tenant/source identity and local revision handling. Owner absence remains a recorded outcome, not automatic deletion. Use an independently approved tombstone/removal policy where business requirements demand it.

### Database or pool saturation

Inspect ready/lease index plans and locking cursor behavior with the exact Oracle driver. Reduce claim concurrency and hot-key visit frequency; apply host-level admission controls for floods of distinct IDs. Account for database-clock reads and quota transactions. More pods can increase ledger contention without increasing external throughput.

## 7. Retention, backup and restore

Projection expiry is not a purge. Implement an application cleanup policy separately, scoped by tenant and consistent with business/privacy obligations. Preserve identity, pending work and required reconciliation evidence; delete dependent records in a reviewed order when the policy permits. This library does not decide legal retention periods.

Back up projection, work, state, quota and identity mapping consistently. Restoring only one side can produce false evidence or stale versions. After restore, keep executors disabled, verify the chosen recovery point and quota/cooldown implications, and revalidate bounded samples before reopening processing. Restore testing is part of release acceptance.

## 8. Oracle test lane

Use an empty, dedicated test ledger schema, never production. The existing smoke test requires an explicit acknowledgement and pre-provisioned DDL:

```bash
export MODELSYNC_ORACLE_TEST_SCHEMA_ACK=DEDICATED_TEST_SCHEMA
# Supply MODELSYNC_ORACLE_URL, MODELSYNC_ORACLE_USER and MODELSYNC_ORACLE_PASSWORD
# through the approved local/CI secret mechanism; do not commit them.
./mvnw -B -ntp -Pfull clean verify
```

The existing `OracleSmokeIT` is only a basic claim/completion/fencing smoke test. It does not by itself test the new migration, IDLE transitions, simultaneous claims, query plans or JPA enlistment. Extend the isolated Oracle lane with those scenarios before sign-off. H2 success does not waive that requirement.

## 9. Evidence and provenance

Preserve test reports for the exact changed source tree, dependency resolution, code review, migration transcript and canary results. The original root `CHECKSUMS.sha256` describes an earlier archive and predates current repository formatting; it is not current release attestation. The delivered overlay includes a manifest for its changed files and base blobs. Generate new whole-release checksums/provenance after building the full repository in your release pipeline.

Oracle constraint metadata is described in the [official reference](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/ALL_CONSTRAINTS.html). The script is an implementation requiring verification against your Oracle environment, not proof that migration has already succeeded.
