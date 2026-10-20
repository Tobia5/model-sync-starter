# Oracle operations, capacity and safety

> **Trigger-extension update:** for job/event/visit APIs, on-demand recurrence and current validation boundaries, use the [handbook](USER-HANDBOOK.md), [arc42 architecture](ARC42.md), [production runbook](PRODUCTION.md) and [review report](REVIEW.md). This document retains the original design/history; original test counts and schema-v1 examples are not the new release evidence.


## 1. Deployment gates

Do not promote this source snapshot solely because the offline core tests pass. Before a live rollout: run the full Maven reactor, Spring/H2 tests, Oracle integration/concurrency tests, real client contract tests, and load tests at your actual row count and provider rate. Verify access control, credentials, driver/database compatibility and retention requirements. There is no production-ready claim in this artifact.

Start with `model-sync.enabled=false` and `worker-enabled=false`. Install reviewed DDL through your normal migration process. Enable infrastructure with the worker still disabled, validate bindings/schema/quota settings, and use a dedicated test tenant. Enabling `repairFromOwner()` is a separate policy decision from enabling the worker.

## 2. Schema installation

Ledger DDL is in:

- `model-sync-persistence-jdbc/src/main/resources/model-sync/schema/oracle-v1.sql`
- `model-sync-persistence-jdbc/src/main/resources/model-sync/schema/h2-v1.sql` (tests/demo only)

The application tables are separate. `model-sync-sample/src/main/resources/sample-oracle.sql` is only the sample entity schema, not a generic customer model.

The library verifies the ledger schema version at startup. It does not run migrations or use Hibernate to create coordination tables. Copy/version the reviewed Oracle script into your Flyway/Liquibase or database deployment process. Use a migration account for DDL and least-privilege runtime credentials for the required tables. Oracle DDL transaction behavior should be handled by that deployment process; this library does not promise rollbackable schema creation.

The first schema stores timestamps as epoch milliseconds in `NUMBER(19)` columns and IDs as explicitly sized character fields. Verify character-set/index-length assumptions for your database/version. IDs are validated for blank/control/oversized values at the API boundary. The ledger includes source identifiers and may be sensitive even though the work ticket is hashed.

## 3. Claim SQL and indexing

The ready index is `(priority, work_status, next_at_ms, work_id)`. The expired-lease index is `(work_status, lease_until_ms, priority)`. State has a validation-age index for operational queries. The application owns its projection's tenant/ID and version indexes.

The claim query uses a locking cursor with `FOR UPDATE SKIP LOCKED`, ordering and JDBC fetch/max-row bounds. It deliberately does not combine Oracle's row-limiting clause with `FOR UPDATE`; Oracle documents restrictions there (S7). This exact query/driver behavior has **not** been exercised on Oracle in the generation environment.

The initial query contains an OR between ready work and expired leases. At millions of rows, inspect its actual plan and logical reads. Separate ready/recovery queries or partition strategies may be justified by evidence. Do not add arbitrary partitioning to a generic starter without verifying Oracle version, licensing implications, unique-index constraints and operational maintenance.

Required tests include multiple independent database connections claiming concurrently, process death after claim, stale completion after reclaim, queue coalescing during an active lease, racing initial inserts and rollback after projection flush. The opt-in Oracle test is a smoke test, not sufficient scale validation.

## 4. Consistent transactions and clocks

Use the same datasource and `JpaTransactionManager` for business projections and JDBC ledger operations. The default validates datasource identity and rejects unknown/XA transaction-manager configurations unless custom infrastructure is provided. A wrapper/proxy datasource mismatch may need deliberate application configuration; do not disable the guard blindly.

`DatabaseClock` queries database current time and converts the result to UTC `Instant`. Verify the selected Oracle driver supports its `OffsetDateTime` mapping. Clock reads are blocking and belong on persistence threads. Operational timeouts and pool settings must bound failed database access too; the starter's HTTP timeout does not bound every database call.

Local transactions use ledger-before-projection lock order. Programmatic business writers and multi-subject operations must observe compatible ordering. Provide idempotent business-level handling for transient deadlock/lock-timeout failures; do not assume that every failure can be transparently retried after side effects.

## 5. Rate budgets and capacity

For N known subjects, c physical calls per subject, request allowance r per minute, daily available minutes m and usable budget fraction u:

```text
minimum cycle days ≈ N × c / (r × m × u)
```

For 5,000,000 subjects, one call each, 100 calls/minute, continuous 24-hour operation and no reserve/error cost:

```text
5,000,000 / (100 × 1,440) = 34.72 days
```

A 30-day full-refresh target is impossible under those assumptions. At a conservative 80 calls/minute admission policy, the same ideal cycle is 43.40 days. Neither WebFlux nor cache annotations create more provider capacity. Deltas, bulk endpoints, inventory exports or a negotiated allowance are what change the economics. 304 responses save payload but may still count as requests under the provider contract.

`maxAge` is a serving rule and freshness target, not a guarantee that every subject will refresh before expiry. Alert on overdue subjects, unverified coverage and age distribution, not merely on a worker being alive.

All participating instances must use the same quota row/group for the same provider credential budget. Different quota groups are independent. Other services using the credential need to participate or have a reserved allowance. Smooth pacing avoids intentional bursts at admission; it is not a universal guarantee about provider-side arrival windows. Leave appropriate margin.

Quota settings are persisted and checked for consistency. Changing YAML while an old spacing remains in `MS_QUOTA` fails rather than silently replacing the shared contract. For a controlled change, coordinate all workers, pause admission, migrate the shared setting through reviewed database operations, update every instance, and resume. Do not treat production quota rows as disposable cache.

## 6. Priority and starvation

Defaults are URGENT/HIGH/NORMAL/BACKGROUND with claim weights 4/3/2/1. Background gets a nonzero selection opportunity and periodic success resets priority to background. The implementation does not cancel running calls for urgent arrivals.

There is no strict global admission priority or reserved urgent token budget. Weighted claim fairness is not a proof of successful-call fairness across competing pods. Inspect realized queue latency by priority. If strict urgency is contractual, add a shared admission scheduler or per-class quota reservation and test starvation under sustained urgent load. An untrusted client must not be able to mark arbitrary work urgent without authorization.

## 7. Retries, timeouts and shutdown

Transient source failures use capped exponential backoff with jitter and a configured maximum attempt count. Permanent adapter/protocol errors or exhausted retries put work into `DEAD`. A new explicit request can revive the subject. Quota denial and provider cooldown defer without renewing age or counting as a source-failure attempt.

The standard clients classify Retry-After and apply shared cooldown via the coordinator. Custom client retries must not silently make additional physical calls behind one permit. Configure HTTP connection/read deadlines, response-size caps and approved redirect behavior. For the blocking `runOne` entry point, a finite transport timeout is essential; the core cannot forcibly interrupt every synchronous library.

The Reactor fetch stage has a timeout; completion transactions are not wrapped in that cancellation timer. Default worker stop cancels subscriptions rather than guaranteeing a graceful drain. Lease expiry/reclaim is the crash-recovery mechanism. Stop admission and allow a drain period in a production lifecycle extension when this is operationally required.

The default has separate persistence and blocking-owner pools but not per-provider pools/circuit breakers. A provider that ignores interruption can occupy shared owner threads until its transport deadline. Multi-provider workloads requiring strict isolation should implement provider-specific pools/bulkheads before rollout.

## 8. Metrics and diagnostics

A ready-made Micrometer binder, authenticated operations API and UI are not included. Add them against the durable state without loading all subjects into memory. Useful measures are due work count, oldest due age, age/maxAge violations, match/mismatch/absence counts, dead work, active/expired leases, admitted/denied calls, cooldown duration and end-to-end latency by binding/priority.

Do not count a quota-denied tick as a successful synchronization. Do not call a dataset complete when only known local IDs were checked. A worker-health signal should separately show database access, provider reachability and coverage/freshness.

Library worker logs intentionally omit payloads and exception messages. Application logging/interceptors must also avoid tokens, source payloads and sensitive identifiers. The ledger is latest state, not an immutable audit trail. Add protected append-only audit/receipts when regulated evidence requires them.

## 9. Security and authority

The starter exposes no unauthenticated HTTP controller. The application must authenticate/authorize enqueue, priority, local inspection and operational recovery. Derive tenant scope from trusted identity, not arbitrary request parameters. Rate limits are not authorization.

Use HTTPS, approved host allowlists and managed secrets for owner clients. URI builders must encode tenant/subject IDs. Avoid logging authorization or raw source data. Secure Oracle credentials, backups and payload retention. A plain hash is not encryption and low-entropy fields can remain guessable.

Automatic remote writes and deletes are deliberately absent. `.repairFromOwner()` permits only local projection updates of mapped source-owned fields. Source absence or identity ambiguity should trigger review, not uncontrolled delete/merge cascades.

Dataset query text and column mappings are developer-owned configuration. Only values are bound parameters. Never expose a generic SQL endpoint for Vista/DWH readers. Use read-only credentials where appropriate.

## 10. Oracle sample profile and smoke test

For the sample, selecting `oracle` disables Hibernate schema creation; a real owner additionally requires `rest-owner` and `owner.base-url`. The synthetic owner and hard-coded demo runner are restricted to `demo`. Do not combine demo and Oracle profiles.

The sample application is one-shot and exits; it is not a production daemon. To run a service, add the starter and bindings to your own Boot application and enable its worker after validation.

The optional Oracle integration test requires an **empty, dedicated test ledger schema** already installed with the reviewed DDL:

```bash
export MODELSYNC_ORACLE_TEST_SCHEMA_ACK=DEDICATED_TEST_SCHEMA
export MODELSYNC_ORACLE_URL='jdbc:oracle:thin:@//test-host:1521/test-service'
export MODELSYNC_ORACLE_USER='dedicated_test_user'
# Inject MODELSYNC_ORACLE_PASSWORD through your approved secret mechanism.
mvn -B -ntp -pl model-sync-persistence-jdbc -am -Poracle-it verify
```

The test refuses to run without the acknowledgment or with preexisting work. It removes only its own exact work/state key. Do not supply production credentials. It does not apply DDL automatically.

## 11. Known limitations checklist

Before expanding scope, account for missing durable inventory/delta checkpoints, no automatic JPA table seeding, no immutable history/outbox, no IBM/Vista vendor adapters, no field-wise multi-owner merge engine, no bulk request cost model, no distributed strict-priority guarantee, no full telemetry package, no automatic cache cleanup, no lease renewal, and no measured million-row query plan.

These are explicit extension boundaries. They must not be represented as completed functionality merely because an interface or design discussion exists.
