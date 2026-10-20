# Verification profiles

| Command | Required work | Excluded work |
|---|---|---|
| `./mvnw -B -ntp clean verify` | Compile all default reactor modules; JUnit unit/contract and bounded H2 tests; compiled-output ArchUnit; source/POM/docs/report guards | PIT, Gatling, Docker, real Oracle, long soak and external providers |
| `./mvnw -B -ntp -Pfull clean verify` | All default work plus selected PIT, Oracle smoke/migration/concurrency/scale, six Gatling simulations and raw-evidence gate | Production providers/data; stable package release |

`full` has no implicit activation. Ordinary checks are in the base build; expensive goals are bound inside explicit `full` profiles. `scripts/check-profiles.py` audits the declared lifecycle and its module dependency edges. A current default Maven execution log must show no PIT/Gatling/Failsafe goals. `scripts/full-preflight.py` rejects skip/filter flags and missing disposable Oracle inputs before expensive goals. `scripts/check-full-reports.py` requires nonempty current raw evidence and the declared PIT threshold. The report generator does not change a failed Maven exit into success.

PIT 1.25.9 with JUnit 5 plugin 1.2.3 targets freshness and application behavior; the target list is in `model-sync-testkit/pom.xml`. The proposed gate is 85% **killed / all generated** mutations, computed from raw XML; killed, survived, no-coverage, timed-out, and run-error counts remain distinct. Zero mutations fail. Compatibility with this checkout's Boot 4.1.1 / JUnit Platform 6.0.3 must be confirmed by a real `full` run before qualification.

The Gatling Java DSL harness is local and synthetic. Its six scenarios cover visits, hot-key coalescing, unsafe values, event/job interaction, cooldown, and local leases. The separate Oracle scale test records 100,000 due rows and Oracle session block/consistent/physical-read work for one admission. This is a measured capacity probe, not a claim that 100,000 remote refreshes completed. Real provider budgets, large concurrency, p50/p95/p99 latency, completed throughput, reconciliation lag, resource bounds, and baseline thresholds need a qualified run and review.
