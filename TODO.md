# Active first-release backlog

Reconciled 2026-10-26. The [acceptance matrix](docs/quality/ACCEPTANCE-MATRIX.md)
is the single current requirement-level record: supported scope, exact source/run,
demonstrated leaf and specific unmet subcriterion. DONE leaves are complete even
where a broader parent remains OPEN. Production remains **NO-GO**.

The first release covers **known-ID owner-to-Oracle/JPA reconciliation** with
concurrent compliant ledger-first writers and one correctly enlisted local
datasource/JpaTransactionManager. See [supported writers](docs/quality/SUPPORTED-WRITERS.md).
Inventory, brokers, vendor connectors and remote owner writes are outside this scope.

## Completed execution and integration

- [x] R01.1–R01.2: accepted configured full at `a0f46d7`, run
  `751ea112-4068-48a0-97fc-c12605a59802`, report `8d07aa7`:83 Oracle methods/18 suites,
  all mandatory mutation/load/runtime receipts PASS. No repeat full was needed for this task.
- [x] R02/R03 scoped leaves: work/JPA/quota NOWAIT, first creation, fencing,
  local atomicity, migration, process-checkpoint recovery and controlled I/O proof.
- [x] R04 scoped leaves: selection, acquisition accounting, durably accepted
  cooldown publication/recovery, eight comparisons, actual worker phases and cursor attribution.
- [x] R05.1: bounded stop/drain with physical-operation observations in controlled fixtures.
- [x] R10.1–R10.2/R13.2: source `551427b`, default run
  `b3db2e0f-e56a-420f-b379-0d1e73f15eb0` PASS222 JUnit/112 Python;
  local39-file artifact set and independent MVC/WebFlux consumer run
  `ca8ae33e-62af-4cc5-935d-cc691de61f60` PASS,3 committed reconciliations each.
  [Consumer report](validation/qualification/2026-10-26-independent-consumers/README.md).

- [x] R06.1/R06.3 scoped security implementation and cheap checks: `8df8c94`,
  MVC 5 / WebFlux 6 methods. Earlier 8e36028 WebFlux Oracle journey passed 30 denials,
  four committed outcomes and 32 concurrent identities; its overall attempt remains FAILED.
- [ ] Final-source MVC/WebFlux Oracle security journeys: source `5dd0381` passed
  matching default (222 JUnit / 116 Python) and fresh cheap consumers (5 / 6).
  Attempt `a85f571c-7cde-4b9e-979e-694155ce34b9` on 2026-10-28 BLOCKED at 6.246–6.259 GiB
  host availability versus 12 GiB; both journeys NOT_RUN, wrapper exit 2, no resources created.
  [Current evidence](validation/qualification/2026-10-28-consumer-security/README.md).
  The 2026-10-26 blocked attempt and earlier MVC assertion failures remain
  [unchanged history](validation/qualification/2026-10-26-consumer-security/README.md).
  No status/CSRF/authorization assertion changed; historical dispatcher cause is unresolved.
  Real identity-provider/production authorization and broader R06 gates remain open.

## Still-open production subcriteria

- [ ] R01.3: required/protected checks, reviewed merge identity and eventual merge-source verification.
- [ ] R02.2/R02.8/R04.9: contending independent-process/multi-pod deployment,
  saturated-worker recovery, unprotected reachable waits and uncertain-outcome operations.
- [ ] R03.2: interruption, stop/migrate/start, backup/restore and supported rollback rehearsal.
- [ ] R04.1–R04.2/R04.8: sustainable real provider/DB capacity, peak resource bounds,
  physical-call/burst contracts, isolation and freshness/lag SLOs.1M seeds were not1M completions.
- [ ] R04.5/R12.2: unrecorded provider responses, uncertain publication recovery,
  receipt retention/maintenance and mixed-version rollout. Durable accepted receipts are already qualified.
- [ ] R05.2–R05.4: production metrics/readiness, alerts, authorized audited DEAD recovery,
  second-operator incident exercise and deployment lifecycle.
- [ ] R06: host authorization, tenant/security threat model, TLS/secrets/SSRF,
  payload limits/redaction and consistency acceptance.
- [ ] R10.3: protected registry publication, authentication and published-artifact consumption.
  Local installation/consumption is complete; remote publication is NOT_RUN and not authorized.
- [ ] R11: supported version ranges, API/reproducibility, SBOM/dependency/license/security
  triage, provenance, namespace/signing and immutable stable-release process.
- [ ] R12.1–R12.3: mapping evolution, retention/purge and application audit history.

## Historical results and deferred scope

The [previous backlog](https://github.com/Tobia5/model-sync-starter/blob/8d07aa785be71a39ac82731606d3de950a61dd2e/TODO.md)
is retained byte-for-byte in the new raw evidence, and all dated archives/reports
remain unchanged. Completed requests to execute full, repair the target fixture,
measure acquisition outcomes and recover accepted cooldowns are not current tasks.

Historical worker timeout: **FAILED/unexplained**, not a newly assigned pacing
redesign or a claim of causal repair. Later regression PASS does not explain it;
reopen only on new evidence or a specific release reproducibility requirement.
Independent plain-UPDATE cancellation: historical **FAILED**, current
characterization **NOT_RUN**; accepted path-scoped applicability is unchanged.
Historical55 missing outcomes stay unclassified; the five old publication failures stay FAILED.

R07–R09 and optional R13.1 extension evaluations are deferred/outside the first
release, with future requirements retained in the matrix. No vendor connector,
inventory, bulk-owner or general runtime rewrite is implied by this backlog.
