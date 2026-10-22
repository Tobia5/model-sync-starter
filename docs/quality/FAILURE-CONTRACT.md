# Shutdown, waits and process recovery contract

## Scope and evidence boundary

This contract precedes the implementation and fault experiments for PR #1's
known-ID owner-to-Oracle/JPA scope. It does not reopen the accepted selector,
pacing, cursor or atomicity milestones. Full-tested `fad6874` and its evidence
remain immutable. The application owns its datasource, transaction manager and
owner transport. The starter must not silently change their global settings.

An owner read is **at least once**. A crash or lost acknowledgement can repeat
a read and spend another admission. A successful projection and reconciliation
state must commit together under the current lease and local revision. Neither
an exception nor cancellation establishes whether COMMIT happened.

## Allowed outcomes and bounds

| Situation | Required outcome | Qualification budget (small local synthetic fixture) |
|---|---|---|
| Graceful stop | Close new worker admission first. Allow admitted work to finish. Report DRAINED only after logical work and tracked physical operations finish. Cancel remaining subscriptions at the drain deadline and report TIMED_OUT if unresolved. | Configurable drain, default 5s; return within drain +500ms dispatch allowance. A timed-out operation is not drained. |
| Noncooperative owner / late result | Fetch timeout may schedule durable retry. Cancellation must not turn a late result into a successful application or release the physical owner capacity prematurely. Track the actual callable/stage lifetime separately. | Test fetch 300ms; deliberate late transport released by the fixture; drain 300ms +500ms allowance. |
| Forced worker-process termination | Kill only the owned child JVM at an acknowledged fault point. Leave Oracle available. Fresh connections establish committed outcomes; a fresh JVM reclaims expired work. | Kill confirmation 10s; lease 5s; fresh recovery 30s, including JVM startup. |
| Pool exhaustion | Connection acquisition fails within the host-configured bound. No success-state/projection partial commit. Returning held connections restores service. | Hikari acquisition 1000ms, observed upper 2500ms. |
| Statement / row lock wait | A real conflicting lock must be cancelled or fail within the configured statement bound. Roll back and return the connection before recovery. Fetch timeout is irrelevant. | JDBC statement 2s, observed upper 5s. |
| Connection establishment / transport stall | Configure only the fixture's Oracle datasource/transport. A stalled socket must not occupy a worker indefinitely. Verify physical calls and pool recovery. | Oracle connect 1500ms, read 2000ms; observed upper 5s per operation. |
| Transaction deadline | Configure only the fixture's local transaction template. Verify expiry and rollback independently of owner timeout. This is a cooperative Spring/JDBC deadline, not an unconditional thread-kill timer. | 2s deadline; check/enforced statement after expiry within 5s. |
| Uncertain COMMIT acknowledgement | The caller records uncertainty. A fresh connection can observe either the old complete state or the new complete state, never a mixed projection/success state. Recovery follows persisted state, not the caller's exception. | Controlled proxy withholds acknowledgement; 2000ms driver read bound, 5s observation allowance. |

Budgets include explicit local scheduling tolerances; they are not production
SLOs or a claim that every arbitrary host, driver or HTTP client is bounded.
Connection acquisition, connect, statement/lock, socket read, transaction,
owner fetch and drain are separate controls. A deployment must configure and
qualify each applicable control, with its external stop grace longer than drain
and transport cancellation/cleanup. Network read timeout is not a wall-clock
deadline for an indefinitely streaming peer. Async stage completion also needs
the owner's documented physical resource-release contract; an arbitrary future
cannot prove that hidden HTTP retries or sockets have stopped.

## Deterministic crash protocol

Each worker child uses the real starter, JDBC ledger, JPA projection and
single-datasource `JpaTransactionManager`. A bounded, nonsecret checkpoint
handshake gates these locations:

1. **CLAIM_COMMITTED:** claim transaction has returned; no owner read yet.
2. **OWNER_RESPONDED:** owner returned, before result transaction.
3. **PROJECTION_FLUSHED:** actual JPA flush completed, before COMMIT.
4. **COMPLETION_COMMITTED:** transaction after-commit callback, before caller
   observes success.

The parent waits for the exact checkpoint, forcibly terminates that child and
verifies process exit before inspecting through fresh transactions/connections.
The first three points must retain the previous complete projection/evidence;
the fourth must expose the new complete pair. Durable intent survives. Expired
leases are reclaimed, obsolete tokens rejected, and newer generations/local
revisions must not be lost. A fresh process may repeat an owner read after an
uncommitted result; a committed on-demand completion must not require a repeat
unless a newer request exists. Owner reads and persisted outcomes are reported
separately. Injected exceptions alone do not satisfy this protocol.

## Physical resource proof and exclusions

Record subscription state, blocking JDBC/owner entries and exits, async-stage
completion, pool active/waiting counts, test-owned sockets and thread/process
termination independently. Zero logical in-flight is insufficient. On a drain
timeout retain an honest residual-operation count; the lifecycle callback only
acknowledges bounded stop, not successful physical drain. Refuse restart while
prior work is unresolved. Never interrupt application-owned schedulers or
unrelated processes/connections to obtain green evidence.

Cheap policy/configuration tests belong to default verification. Oracle,
independent-JVM and transport-fault checks are full-only; scoped diagnostics
remain DIAGNOSTIC_ONLY / full NOT_RUN. A final full run retains every existing
atomicity, fencing, quota, architecture, mutation, scale and semantic-load gate.
Racing inserts, multi-pod availability/SLOs, real provider retry/fanout budgets,
arbitrary network partitions, XA and production operational qualification stay
open unless separately evidenced.
