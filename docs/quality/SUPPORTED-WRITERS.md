# First-release writer model

The intended first release reconciles known external IDs from an authorized owner
reader into Oracle/JPA using one correctly enlisted datasource/JpaTransactionManager.
Multiple concurrent compliant application instances are in scope. Single-instance
deployment is not a workaround for an unqualified multi-instance path.

Starter-owned MS_WORK, MS_STATE, MS_QUOTA and MS_META are written only through
the schema-compatible starter adapters and reviewed versioned migrations (workers
stopped for migration). Instances use identical bindings, quota configuration and
compatible policies. Ledger locks precede projection locks; successful state and
projection commit atomically under the current token/generation/revision. Quota
admission and cooldown use their separate authoritative committed transactions;
owner I/O never holds those locks. Migrations/admin maintenance do not run
concurrently with worker DML in the supported writer model.

Applications own projection mappings and local business writes. Supported business
mutation paths preserve ledger-before-projection order and optimistic local revisions.
The qualified mapping here is the sample's single primary table, no additional
flush targets, cascades, trigger side effects or hidden owner I/O. A custom mapping
must explicitly implement the lock capability and supply its own operation-level
qualification. Arbitrary external SQL, out-of-band writes to starter tables and
arbitrary triggers/mappings are not added to the first-release support promise.

This boundary removes no legitimate first-insert requirement: two compliant
instances can race to create MS_WORK or MS_QUOTA; a missing projection can encounter
a legitimate application insert; first MS_STATE must remain protected by the work
fence. Those paths remain listed in the [coverage matrix](LOCK-POLICY-COVERAGE.md).
Known owner IDs do not imply local rows exist. Ordinary INSERT and unique/FK checks
are unchanged and not bounded by SELECT NOWAIT on a missing row. Existing-row tests
cannot qualify concurrent registration. Do not declare complete bounded persistence
or successful full qualification while applicable controls are missing.

The [accepted decision](LOCK-POLICY-DECISION.md) is scoped to its measured operations.
The original plain-UPDATE failure remains relevant to unprotected reachable SQL,
including supported extra writes if a later contract adds them. No release gate is
silently made NOT_APPLICABLE by this writer model; production remains NO-GO.
