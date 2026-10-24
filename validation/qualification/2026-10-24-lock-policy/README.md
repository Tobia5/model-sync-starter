# Production point-lock integration evidence - 2026-10-24

**Default PASS; focused Oracle21/21 PASS; full NOT_RUN; production NO-GO.**
This qualifies the measured adapter paths only. It does not replace the original
failed cancellation gate or qualify all reachable persistence operations.

## Final exact source and runs

Clean source `2eea1d849dfd8d5f7f0ee1ee6f3386412b282477`, tree
`8338eff0badecc4be2b8855f1e4e9fedbc8da0d6`.

- Default `mvnw.cmd -B -ntp -s .mvn/qualification-central.settings.xml clean verify`:
  run `fb25cfb1-075e-407a-8ca2-8bfb276278b3`, exit0/107.286s, collector0;
  **141 JUnit/31 XML suites,72 Python guards**, zero failures/errors/skips.
- `python scripts/full-oracle-container.py --lock-policy-diagnostic`:
  run `d7f2d3fe-348c-41a5-89b3-0f7ed0785200`, infrastructure attempt
  `021c1335-b225-4df6-963d-fea55337c9b8`; preflight/launcher/wrapper0,
  157.537s supervision. Seven integration cases/11.473s, seven retained DEFAULT
  atomicity cases/8.582s, same seven inherited NOWAIT cases/9.429s passed.
  Counts are JUnit launcher summaries, not Failsafe XML. The strict detailed-suite
  validator passed on clean final source before the later report changes.
- Standalone Git Bash `scripts/test-triggers.sh`: exit0,86 core/43 trigger
  assertions, separately counted. No current PIT, Gatling, large seed or full run.

| Production case | Observed result |
|---|---|
| Disabled policy | Compatible locking/refresh/DML SQL and successful completion; healthy pool. |
| Existing-work enqueue refusal | Oracle54,12ms to refusal/85ms to outer transaction return; SID61/serial281. Guard rollback, unchanged generation1/WAITING, no accepted enqueue or owner call. |
| Result refusal/recovery | Oracle54 from actual JPA lookup,7/13ms; SID61/serial54156. Projection before/version0/no success; separate LOCAL_CONTENTION retry and unchanged cooldown.2 synthetic reads produced1 later completed sync. |
| Metadata also locked | Result refusal5/10ms; SID60/serial20807. One metadata call refused in3ms, original failure retained; LEASED token/generation1/attempts0 remained. Explicit post-cleanup expiry allowed another read and1 completion. |
| First inserts, uncontended | Ordinary work/projection/state insertion completed; conflict bounds remain UNQUALIFIED. |
| Enabled safety/refresh | Lookup NOWAIT; refresh reads under its already-held lock. Independent observer refused54 immediately before flush. Generation, old-token and local-revision controls passed. |
| Actual Reactor worker |3 successful synthetic owner reads,0 completed syncs, DEAD/attempts3/LOCAL_CONTENTION.4 ticks/1 empty/3 failed/0 fetched; maximum in-flight1. DRAINED/physical0 plus exact-session/pool observations and owned-thread cleanup. |

For all manual refusals the blocker remained held: exact session INACTIVE/SQL*Net
idle/LOCK_WAIT0/TX_ACTIVE1 before rollback; LOCK_WAIT0/TX_ACTIVE0 afterward.
Guard writes/locks rolled back, subsequent pool queries worked. Times include
setup/observation/rollback; no isolated-execute latency or production SLO is claimed.
The copied diagnostic cause contains code/state without an ORM entity/session or
JDBC resource. Default exception behavior remains compatible.

## Raw artifact and source attribution

[Raw evidence](raw-evidence.zip): **388 files/752638 bytes**, SHA256
`3ffed5ecc945db4613ec6497ff39d92beeafba60ea45f6f711f66621df8ebf07`.
ZIP CRC and every input byte/hash verified. Bounded standalone48-hex and nonempty
XML-secret-property scans found0; this is not a universal secret audit.

Contains original and normalized logs, default XML, diagnostic summaries/details,
all source/run/change digests and per-file manifests, failed red/development and
default attempts, three separate Oracle attempts, settings/helpers, source-JAR
analysis hashes, run-scoped QM and independent admission/cleanup receipts.
[Derived analysis](execution-analysis.json) preserves exact run identities and
[observed SQL](observed-sql.sql) contains the actual statements, without rewriting.
Counter values are not converted into cursor deltas, throughput or provider calls.

Preserved outcomes:

- Command setup exit1 before tests; corrected dirty-development red run4 tests,
  3 failures/0 errors, exit1, at baba54c plus its recorded change digest.
- f32213f default0d3f9aea-04bb-4c33-b899-e47dbc36a39b: FAILED/exit1,
  24 JUnit/1 error; wrong optional-hint assumption in new test.
- 6465adc defaultf8ed800d-198d-4ae5-af56-d78f98c1748e: FAILED/exit1,
  139 JUnit/1 error; new mock restubbing invoked an earlier throwing stub.
- 5fedde9 default14383024-8a93-4a2f-98f5-dba4b11a3cf2: PASS/141 JUnit;
  diagnostice476fe40-f285-4e14-bf07-8c50372e544c: FAILED/exit1/5 of7,
  incorrect duplicate-refresh SQL expectation and state-changing wait predicate.
  Atomicity suites did not execute in that attempt.
- 118526e default97804af5-ea78-4851-a79f-23a8c6ca04fc: PASS/141 JUnit;
  diagnosticb7564920-214b-40cc-84c8-c626c379244c: PASS/14 of14. This predates
  the detached-cause fix and explicit enabled-policy atomicity variant, and is not
  substituted for final-source evidence.

The pinned Hibernate source explains its refresh lock elision. The corrected test
proves lock retention instead of requiring redundant SQL. The one-shot recovery
observation is sticky. No existing gate, workload, timeout or score was weakened.

## Resources, cleanup and open requirements

Final admission eb11763a-19a5-4500-a0a5-eac5258e7845 passed after default collection:
12.999/13.104/13.073 GiB versus unchanged12 GiB; full planning remains18 GiB.
Oracle6 GiB/4 CPU, JVM512 MiB heap/128 MiB direct; pinned Oracle26 image, generated
credentials, separate schemas and loopback ports. Runtime3/observer2 connections;
acquire1s/connect1500ms/read10s; manual transaction template2s; actual manager
default timeout-1, not silently replaced by a global deadline. Actual worker has
one lane/persistence/owner thread, queue8, retry200/400ms,max-attempts3,shutdown2s.

All three containers and their -data volumes independently absent:
`msq-d8763410c914`, `msq-6b736e92dadb`, `msq-a125147b196d`. No emergency release
earned mitigation credit. Owned pools/scheduler threads/executor ended, physical0,
owned tables/constraint removed; no observed owned residue. Unrelated resources
and global settings were untouched.

First-insert conflicts, quota registration/locking, extra flush targets and arbitrary
SQL/mappings/transport/COMMIT paths remain unqualified. Known IDs do not remove local
insert races. Original plain-UPDATE cancellation and active full requirement remain
unchanged; the [path-scoped proposal](../../../docs/quality/JDBC-CONTAINMENT-PROPOSAL.md)
needs explicit reviewed acceptance before any semantic gate change/full requalification.
See [QM](../../../QM.md), [coverage matrix](../../../docs/quality/LOCK-POLICY-COVERAGE.md)
and [handbook](../../../docs/USER-HANDBOOK.md). Accepted eb18209/baba54c and every
earlier archive remain unchanged. No merge, main change, workflow bootstrap or publication.
