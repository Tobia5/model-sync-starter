# Layer and responsibility review

The prepared `files/` input bundle was unavailable in this checkout. The observed local main was `a36b3ab88ef5200fb51bd2b55956c302ae5a0392`, two commits ahead of the observed remote main `a555ffaf8681b6dcc2597f16a84bf5b2a45c11d6`. Those commits already supplied job/event/visit, on-demand recurrence, arc42, handbook, TODOs, and snapshot packaging; no older cumulative patch or starter ZIP was applied.

## Direction

`configuration/inbound adapters -> application -> ports + pure domain`. Outbound adapters implement ports. Production module boundaries remain library, Spring integration, JDBC/JPA/REST/Reactor/WebClient, autoconfiguration, starters, and sample. Architecture and performance artifacts are test-only and are not installed or deployed.

The domain package holds immutable keys, projections, state, freshness, and digests. The application package owns binding and coordination; `ModelBinding` stays there because it holds owner and projection ports. The port package keeps the compatible sealed `OwnerPort` / `BlockingOwner` / `AsyncOwner` hierarchy together. A common HTTP classification parser moved to core so REST and WebClient adapters do not depend on one another.

`SyncCoordinator` retains the visible claim transaction and delegates planning to `SyncPlanner`, blocking owner execution to `OwnerCallExecutor`, lease/version guarded application to `SyncResultApplier`, and retry/cooldown to `SyncFailurePolicy`. `SyncCompletion` centralizes periodic versus on-demand completion. There is no HTTP call in planning/application transactions. Local source values remain detached at the port boundary.

## Contract review

- Owner authority: only the registered owner returns source values; absence does not delete local projections.
- Tenant and work identity: length-prefixed work key includes binding, tenant, and subject; local and JDBC stores compare full identity.
- Lease/version: completion checks current token and local revision before applying a result. An event during a lease leaves a later generation pending.
- Local atomicity: projection CAS and ledger completion share the local transaction; failures roll back both.
- Passive behavior: visits do not renew validation time, bypass backoff/cooldown, increment a leased generation, or revive DEAD work.
- Quota: admission is separate from the source fetch. The current contract counts a fetch admission; physical retry/bulk/MDM call budgets require further bounded provider adapter qualification before release.
- Recurrence: PERIODIC schedules due work; ON_DEMAND becomes IDLE after completion and needs schema v2. Event/visit triggers remain independent of worker execution.

ArchUnit executes in the default reactor against each compiled production module. Source/POM guards check module edges and compilation output. Negative fixtures prove the dependency, cycle, and field-injection rules reject representative violations. These structural rules do not prove all SOLID properties; the responsibility review above identifies the behavior each service owns. See [public migration map](PUBLIC-TYPE-MIGRATION.md).
