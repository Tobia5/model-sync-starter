# Target fixture and worker deadline diagnostic

This finite diagnostic continues report `b7f3cb5`; the failed full execution of
`7c1abe5` and every earlier artifact remain immutable. No production lock,
cooldown, applicability or queue change is proposed by this diagnostic.

## Declared matrix and budgets

1. A controllable synthetic owner supplies A/validator A, then B/validator B through
   the real gateway/coordinator. Fresh connections inspect each committed pair.
   Only the changed-existing-row phase supplies the bounded SQL evidence.
2. Deterministic virtual-time tests compare four synchronized fixed-delay lanes
   with adaptive lanes, with real reservations and authoritative 20 ms admission.
   This tests the synchronization hypothesis without attributing a runtime cause
   from an approximate throughput ratio.
   Unknown-hint controls are labelled separately: MemoryQuotaGate itself exposes
   no hints, so those controls do not prove reservation behavior. ModelHintQuota
   adds a known hint after actual delegated admission for the reservation cases.
3. Oracle diagnostic cells run independently: fresh/fixed, fresh/adaptive,
   recorded-statistics/fixed, recorded-statistics/adaptive. Each keeps four lanes,
   runtime pool8, persistence4/owner8 threads, 20 ms owner spacing/5 ms latency,
   ten warmup completions, 160 measured completions and the original 20 s bound.
   Failed cells do not suppress later cells. No absent cell is PASS.

The history cells apply the recorded final scale fixture optimizer statistics to
small synthetic data, retaining the original raw statistics. They are explicitly
a statistics-only predecessor model, not a reproduction of million-row physical
segment/cache history. Record actual table/index statistics, rows, quota/pending
state, owned sessions and thread/pool cleanup separately. No global cache flush,
resource increase, million-row seed or unchanged comparison is permitted here.

The copied table estimates are NUM_ROWS=100000/BLOCKS=2260, from source7c1abe5,
run d7fd02b7-1d9d-430c-a8ef-dfa883e8add6, final `all-future-with-stats` entry
in `oracle-scale.json` (DB-local analysis timestamp2026-10-25T18:20:38).
Only these two table estimates are set. Index statistics, physical high-water
mark and cache state are measured rather than claimed reproduced. Original raw
archive SHA256: f430ab0dc388fe1f413862366d492dbe0feed7c30c838c808332fd1e096fccf5.

Bounded test-only traces record lane/generation and tick/wakeup times, coordinator
claim/prepare/finish durations, local reservation before/after transitions, hint
deadlines and clocks, eligible binding sets and selector outcomes. Endpoints record
committed rows and eligible remaining work. Missing lane attribution or dropped
events prevents a diagnostic from proving its hypothesis. Traces retain detached
types/codes only, never exception graphs or credentials. Recording overhead and
observer queries are reported; observations do not authorize provider I/O.

Use the approved disposable wrapper and matching fresh default evidence. The
small diagnostic requires the existing 12 GiB admission, Oracle6 GiB/4CPU and
bounded JVM settings; full retains18 GiB admission and all83 methods. No full run
occurs until focused requirements pass, or an unsupported compatibility threshold
has an explicitly reviewed replacement. Never raise the current bound silently.

## 2026-10-26 recorded result and unadopted criterion candidate

Sourcebaa8d26/tree3f27d829 passed default222 JUnit/102 Python. Known-hint virtual
controls measured80 fixed versus160 adaptive completions in20s; this is a
counterexample to a universal fixed-mode guarantee, not an actual Oracle cause.
The Oracle matrix was NOT_RUN: resource admissioncd0117b8 measured10.951-11.004GiB
versus unchanged12GiB. Docker/disk passed. No resource retry or wrapper launch.

Candidate for explicit review after runtime measurement: fixed250ms remains a
measured20s compatibility control; configured adaptive retains160/20s. All other
requirements remain. This candidate is **not adopted**. OracleWorkerIT and the
full report validator still enforce160/20s for both modes. No full execution may
be substituted with these controls. Actual SQL, per-lane Oracle observations,
predecessor state and cleanup are still NOT_RUN. [Current QM](../../QM.md).
