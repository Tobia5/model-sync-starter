# Target fixture repair and worker deadline diagnostic - 2026-10-26

**Default PASS. Focused Oracle admission BLOCKED. Current full NOT_RUN. Production NO-GO.**

Tested clean source **`baa8d265f94bb38e7baee93c0dbb999d8e4afde6`**, tree **`3f27d8290820022c4a07514b14113defc9ce3667`**,
branch `review/layered-refactor-qm-20261020`, repository `Tobia5/model-sync-starter`.
The later report-only commit is not claimed as tested source. Source7c1abe5,
reportb7f3cb5, the failed integrated run and all18 earlier archives are preserved.

## Executed checks and exact blocker

- `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify`:
  PASS, run `9bb0a407-7bf4-43e4-ba50-eb7c0f6ef7ea`,222 JUnit tests/102 Python guards/41 XML reports,
  zero failures/errors/skips; build/qualification/execution-audit/collection exits0.
  Supervisor build runtime349.441s. The five virtual-time controls and27 semantic
  SQL matcher cases are included in222, not extra tests.
- Git Bash `scripts/test-triggers.sh`:86 core/43 trigger assertions, exit0;
  these are separate standalone assertions, not JUnit counts.
- After build and collection, `python capacity.py diagnostic` (archived helper):
  **BLOCKED, original Python exit2**. Host available10.985/11.004/10.951GiB,
  below unchanged12GiB. Docker19.349GiB available; C66.800/D1069.775GiB free;
  those checks passed. One bounded three-sample admission; no retry/resource change.
- `python scripts/full-oracle-container.py --target-worker-diagnostic`:
  **NOT_EXECUTED**. Wrapper, preflight Maven and JUnit launcher exits are null /
  NOT_RUN. Target fixture and all four runtime cells are NOT_RUN. No new Oracle
  run ID, generated credentials, container, volume or schema exists for this block.
- The blocked receipt has attribution NONE for Oracle. Separate collection of
  the actual matching default plus the admission blocker exited0; it retains
  default run attribution and full NOT_RUN. No leftover Oracle/PIT/Gatling report
  is attributed to this attempt.

JDK21.0.12.1, Maven3.9.9, Python3.12.10 and Docker29.8.2 were observed.
Boot4.1.1/JUnit6.0.3/Hibernate7.4.5.Final remain pinned/resolved by compilation.
Approved settings SHA256:
`ef6dc911e57f86840ab529882c579e4128e9dc05ba6077640b622b852b726940`.
The target remains `oracle26-nowait-ledger-first-v1`: point/quota/JPA NOWAIT,
Oracle26 INSERT NOWAIT, DURABLE extension1 and qualified ledger-first/JPA mapping.
Actual Oracle platform, policy/mapping/enlistment and migration receipts on this
source are **NOT_RUN**, not inferred from properties or earlier receipts.
The pinned image, Oracle6GiB/4CPU, small JVM512MiB/128MiB direct and full18GiB
admission remain unchanged. No full, scale, comparison, PIT or Gatling execution
occurred during this diagnosis.

## Implemented fixture correction (Oracle proof still pending)

The synthetic owner is controllable: A/validator A, then changed-B/validator B.
Both requests use the actual public gateway/coordinator. Independent observer
connections assert committed projection/state pairs, generations1/2, APPLIED/IDLE,
validator propagation and local revision increment. Capture resets before the
changed-existing-row phase and is bounded to256 statements of8192 characters;
phase/SQL/overflow/failure-type evidence is written even on assertion failure.

A test-only lexer recognizes the relevant projection SELECT with terminal
`FOR UPDATE [OF column-list] NOWAIT`. It rejects plain locks, WAIT, SKIP LOCKED,
unrelated statements and comment/literal-only matches, including literals used
as OF columns.27 parameterized cases passed. These SQL strings are unit fixtures;
**no new Hibernate-generated Oracle SQL was observed**. Full validation now also
requires the fresh, source/run-bound two-phase SQL proof. Production locking was
not changed to satisfy the fixture.

## Worker observations: measured versus unknown

The only production worker change tags existing lanes in Reactor context for
identity across scheduler hops; delays, reservations, quotas and cancellation
semantics are unchanged. A default test proves all four lane IDs survive real
persistence/owner scheduler hops. Oracle tracing is test-scoped and bounded:
40,000 events/cell, explicit overflow/unattributed counts, tick/claim/prepare/
finish and scheduler-wakeup times, reservation before/after snapshots, actual
hint/claim clocks, provider-filter/selector reasons and detached failure codes.
No entity-bearing exceptions, connections or credentials are retained in traces.

| Virtual-time control | Known hints | Owner ms | Completions at20s | Grants | Ticks | Late unapplied |
|---|---|---:|---:|---:|---:|---:|
| fixed250ms | yes |5|80|80|320|0|
| adaptive5ms | yes |5|160|160|365|0|
| fixed250ms | no |5|80|80|320|0|
| adaptive5ms | no |5|160|160|365|0|
| fixed250ms | no |0|81|81|324|0|

All use four synchronized lanes,160 offered items and authoritative20ms admission;
known-hint cases use real scheduling reservations and assert zero deferred
admission races. Unknown-hint controls are explicitly separate. These executable
counterexamples disprove a universal160/20s guarantee for fixed-delay mode. They
**do not establish the actual Oracle failure's cause**. Earlier7c1abe5 measured
84/160 completions remains a failed observation, not proof inferred from4.2/s.

The declared Oracle matrix is fresh-data/fresh-host fixed/adaptive and recorded-
statistics fixed/adaptive; all four are **NOT_RUN**. It retains four lanes,
pool8/persistence4/owner8 threads,160 completions and the20s condition. Each cell
executes independently, retaining failures. The history model sets only the
recorded MS_WORK estimates100000 rows/2260 blocks; it does not claim to reproduce
physical segment, index or cache history. Actual snapshots and residue observations
are implemented but unexecuted. No production scheduling correction is justified
yet; synchronization, reservation timing and predecessor-state effects remain
runtime hypotheses.

## Narrow criterion candidate - provisional, not adopted

For explicit review after the Oracle matrix: preserve the fixed250ms compatibility
behavior and its20s observation window as a measured control, recording completed
and still-durable work without claiming160/20s performance. Keep160/20s mandatory
for the configured adaptive worker. Preserve every concurrency, provider-admission,
idle/wakeup, atomicity, fencing, recovery, shutdown, mutation and load requirement.
This is a proposed change to one fixed-control throughput criterion, not an
implemented waiver. The current test and QM validator still require160/20s in
both productive phases. No deadline/resource increase or qualification selection
change was made. No full run is justified while this applicable gate is unresolved.

## Evidence and remaining work

Current full inventory is unchanged:83 methods/18 suites. None executed on this
source; the separately retained7c1abe5 full remains FAILED (81/83, target SQL proof
and fixed worker timeout). Historical cancellation remains FAILED/current
characterization NOT_RUN. Accepted NOWAIT/cooldown/accounting milestones remain
within their earlier measured scopes and are not reopened by these unit results.

Next concrete prerequisite: at least12GiB available host memory, with the same
Docker/disk checks and matching default, to launch the declared small matrix once.
No additional generic Docker approval is required within the existing scope.
Only after focused requirements are resolved, or the exact replacement criterion
is explicitly reviewed, may final clean-source full qualification proceed at18GiB.
Unrecorded responses, uncertain outcomes, receipt retention, security, operations
and all other incomplete parent release gates remain open. No merge, main change,
workflow bootstrap, package publication or unrelated-resource mutation occurred.

[Root QM](../../../QM.md).
Raw archive SHA256: `9f1546d1f22d576e808265ad468ef09e09be5518895ea22061aa1230e806a37e`.
The archive contains 241 entries (483,820 bytes); CRC and every entry digest passed.
[Report verification](report-verification.json) records passing docs/architecture/
diff checks and preservation of all 18 prior archives and the historical QM suffix.
No owned Oracle resources were created, so no cleanup action or successful Oracle
drain is claimed. The root QM retains the prior content byte-for-byte.

---

## Attempt inventory

| Source / attempt | Scope | Original exits | Outcome |
|---|---|---|---|
| Unsealed development1 | tests with static exec guards intentionally not selected |1|AsyncOwner builder compile error; no qualification claim|
| Unsealed development2 | same development command |1|CooldownRequest test-instrumentation package compile error|
| Unsealed development3 | same development command |0|Development Java tests passed; not clean default evidence|
| ef292db / default-7e379101 | clean default |build1, qualification1, audit0, collector0|102 Python guards/one cap-condition textual guard failed; no current JUnit marker|
| 2af72dc / default-58224e98 | clean default |all0|218 JUnit/102 Python PASS, run11d6415e-d76a-4c14-a531-dc0b9cd55734; no Oracle execution|
| baa8d26 / default-a6f7a27f | final clean default |all0|222 JUnit/102 Python PASS, run9bb0a407-7bf4-43e4-ba50-eb7c0f6ef7ea|
| baa8d26 / cd0117b8-f7fa-49cd-b50c-3d6fef693899 | small diagnostic admission |Python2|Host memory blocked; wrapper/Oracle NOT_RUN|
| baa8d26 / blocked-collection | actual default collection plus separate blocker |0|No new Oracle attribution; full NOT_RUN|

Development commands were `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml
-Dexec.skip=true -pl model-sync-sample -am test`. They did not select full or skip
JUnit, but did not run the static exec guards and are not normal/default qualification.
Their working trees were unsealed; no exact tested source identity is fabricated.
Later clean default runs bind the actual final code. A separate Python negative-test
setup failure was corrected to keep the same run marker while corrupting only the
projection proof; final102 guards passed. The ef292db cap guard was extended to
include the additional small mode, retaining512MiB/128MiB for every prior mode.
No cancellation reproducer/assertion/applicability was changed.

## Archive contents and integrity

- `source-commits.txt`, `review-changes.patch`, `frozen-source-end.json`: exact final source/tree, clean state and proposed review changes; intermediate commits are not claimed Oracle-tested.
- `default-*/`: raw Maven logs, original result JSON, separate audit/qualification/collector outputs and fresh report hashes. The marker-less failed source is explicitly unqualified.
- `diagnostic-blocked.json`, `capacity-command.json/log`, `scoped-capacity-*.json`: original bounded admission, Python exit2, all five selected Oracle methods/cells NOT_RUN, no generated infrastructure and no invented runtime identity.
- `blocked-admission/QM.md`, `evidence.json`, `raw-manifest.json`: actual run-scoped default evidence with the separate blocker, not a failed/unexecuted Oracle report.
- `standalone-contracts.*`:86/43 assertion result, exit0, separate from JUnit.
- `session.py`, `capacity.py`, `diagnostic-reader.py`: exact local orchestration/collection. The reader was prepared but NOT_RUN because no Oracle reports exist.
- `preservation.json`: hashes of all18 earlier archives and b7f3cb5 root-QM git content. The historical unclassified55 and previous failures are untouched.
- `raw-archive-manifest.json` and `SHA256SUMS`: entry and archive seals. The credential-format scan has zero matches; it is a bounded check, not a general secret detector.

The target fixture and independent four-cell diagnostic are executable through the
new explicit CLI only; the ordinary build compiles these classes without Oracle.
Full selection remains additive/manual with the original83 methods and all PIT/
Gatling gates. The proposed criterion is not reflected in test selection or green
qualification. There is no current Oracle SQL/plan/wait/cleanup result to substitute
with earlier reports. Expected SQL examples and virtual-time controls are labelled
as such. Release qualification remains incomplete.
