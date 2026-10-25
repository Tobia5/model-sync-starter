# Durable provider cooldown publication

The opt-in `model-sync.jdbc.cooldown-policy=DURABLE` requires the explicit
`oracle-cooldown-v1.sql` extension (extension version1 in MS_META). DIRECT and the
old constructors keep their existing schema/API behavior. Bounded Oracle qualification
additionally requires the named Oracle26 INSERT/point/quota policies. No automatic DDL,
external broker, general workflow service, owner retry or admission refund is introduced.

## Ordering and recovery contract

1. Deadlines use the ledger millisecond precision, rounded up once so fractional
   provider deadlines are never shortened. On the first application observation of a provider rate-limit outcome, compute one
   absolute deadline with the configured clock. The operation ID hashes work identity
   and the current lease token. One original lease/outcome has one stable identity.
2. Independently commit an immutable MS_COOLDOWN receipt: operation ID, provider group,
   absolute deadline, acceptance time and PENDING status. This acceptance never locks
   the quota/work/projection row. Retries keep the same request. Duplicate identities
   return the canonical first-accepted deadline; they cannot extend it or change group.
3. In a fresh transaction, lock quota first, then the exact receipt, both with the
   selected point-lock policy. Materialize max(existing quota deadline, all accepted
   pending deadlines, this receipt). Mark this receipt APPLIED in the same commit.
   A successful callback is not completion until the transaction returns committed.
4. Authoritative admission locks quota, then reads the committed pending maximum.
   It denies before that maximum and never spends a permit on a denied request.
   Under that same quota fence it can materialize/recover one oldest pending receipt.
   Explicit bounded recovery does not consume an admission or invoke the owner.
5. A durable acceptance that overlaps an admission's already-executed pending-guard
   read cannot recall that in-flight admission. That admission may commit one grant
   afterward. An admission beginning after acceptance returns must observe the barrier
   (or refuse on contention). Already committed/in-flight owner reads are not cancelled.
   Admission operation linearization is the guarded pending read, not its later return.
6. Pending acceptance survives process death; another instance reads the same barrier
   and can finish materialization. APPLIED receipts are retained for idempotent delivery
   after uncertain acknowledgments. No automatic receipt purge/retention promise is made.

Receipt updates are serialized by the quota fence. Acceptance inserts never proceed
to quota locking in the same transaction. No foreign key to quota is used: a held quota
row must not prevent recording the response. Registered groups are checked consistently;
concurrent quota deletion and out-of-protocol receipt writes are unsupported. Queue
selection, work fencing/generations, local revisions and projection atomicity are unchanged.

## Refusals, errors and shutdown

Confirmed callback SQL54 refusal is translated only after successful rollback and
connection return. Up to four fresh acceptance transactions and four fresh materialization
transactions are allowed; three jittered sleeps per phase total at most70ms (5..10,
10..20,20..40ms). Sleeps occur outside all caller/attempt transactions and connections.
This is a retry budget, not a substitute for connection/statement/network/COMMIT bounds.
Unexpected failures and uncertain completion are not retried as known refusals.

After accepted materialization contention exceeds this budget, return DURABLE_PENDING,
never APPLIED. Work metadata uses COOLDOWN_PENDING and the canonical deadline. This is
safe shared deferral because every new admission reads the pending barrier. If acceptance
itself fails, throw an infrastructure error; only a local fail-closed marker is retained
until the requested deadline. It is not distributed knowledge. A recovered attempt that
hits that local marker is COOLDOWN_UNRECORDED, not successfully handled DEFERRED or an
owner/network error. Other provider groups remain eligible. Monitoring/recovery of an
unrecorded response and unknown physical transaction outcomes remains operational work.

If cooldown commits but work metadata fails, the receipt/floor remains committed and the
lease remains recoverable. Re-publication of that identity returns the original deadline;
it does not repeat the owner read, spend/refund a permit or recompute a longer delay.
Interruption stops retry sleeps. Accepted pending state remains authoritative during bounded
shutdown; subscription cancellation is not proof of physical completion. Existing separate
physical drain, transport, uncertain-COMMIT and shutdown gates remain mandatory.

Logical requests, durable acceptances, immediate APPLIED/PENDING receipts, raw transaction
attempts/refusals and final persisted outcomes are distinct measurements. Final comparison
qualification requires every logical receipt resolved, no unknown/uncertain outcome and
valid worker concurrency. Historical five publication failures and the unclassified55 remain
unchanged evidence. This contract does not claim provider knowledge before durable acceptance.

## Legacy operation and extension lifecycle

`QuotaGate.cooldown(group, until)` remains the direct, one-shot compatibility SPI.
It has no outcome identity and does not promise durable pending delivery. The
standard coordinator now uses `publishCooldown(CooldownRequest)` instead. Custom
ports opting into DURABLE must implement that capability and forward it through
wrappers. The named fixture rejects a legacy/fallback gate. The existing refusal
controls continue to exercise the old operation and are not rewritten as successes.

Fresh v2 schemas include the optional extension. Existing v2 installations must
stop writers and explicitly apply `model-sync/migration/oracle-cooldown-v1.sql`.
The adapter checks extension version1 and columns, never creates/migrates tables.
Two compliant acceptors may race on one outcome identity; native Oracle26 INSERT
NOWAIT bounds that refusal, and a new transaction after competitor resolution
returns the canonical row. DIRECT/older Oracle modes retain their existing limits.

The interval between provider response and durable acceptance, inability to record
acceptance, uncertain commit inspection, receipt retention/maintenance, security and
operations remain release requirements. A passing configured test suite does not
prove those operational gates or production readiness.

## Current measured scope

[Source-bound report](../../validation/qualification/2026-10-25-cooldown-publication/README.md)
retains the initial launcher failure, subsequent7/7 Oracle PASS and8/8 controlled
comparison PASS.104 requests all materialized;10 physical materialization refusals
were retried without another owner call. The current integrated full is BLOCKED
by18GiB resource admission; default PASS and focused PASS are separate evidence.
Every sharing instance must consume DURABLE pending state. A DIRECT participant
is outside this shared-barrier guarantee; coordinate rollout/migration with writers
stopped. Unrecorded response windows, unknown completion, retention, security and
operations remain release requirements.
