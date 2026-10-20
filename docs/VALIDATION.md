# Validation report

Artifact: Model Sync `0.1.0-SNAPSHOT` · prepared 2026-10-19.

## Executed in the generation environment

| Check | Result | What it establishes |
|---|---|---|
| Java 21 core + testkit compilation | PASS | Dependency-free core and test fixtures type-check with `javac --release 21` |
| Offline core contracts | **77 passed, 0 failed** | Executed behavior against in-memory transaction/ledger/quota/projection resources |
| Java source parse | **82 files, 0 syntax errors** | Every Java source parses with the JDK parser; no framework symbol linking |
| Maven POM and module check | PASS, 13 modules | POM XML validity, known internal modules, acyclic internal dependency graph |
| Core import boundary | PASS | No Spring, Reactor, JPA/Hibernate or JDBC imports in core |
| Auto-configuration registration | PASS | Imported auto-configuration names resolve to project source files |

Logs are included in `validation/`. The parse check intentionally does not claim full compilation of Spring, Reactor, JDBC or JPA sources. The architecture check is not a substitute for a Maven dependency-resolution build.

Core scenarios cover typed route ambiguity, identity separation, age thresholds, local-only reads, freshness versus retention, conditional requests, expired/evicted cache, verify-only mismatch, no implicit deletion, quota denial/cooldown, weighted selection, request coalescing/generations, concurrent in-memory callers, lease reclaim/fencing, stale local-revision rejection, atomic in-memory rollback, retries/dead work, async core owners, dataset slice/coverage/digest/totals checks, and invalid/null adapter results.

The memory resources simulate local transactions. Their success is not proof of Oracle isolation, JDBC statement semantics, JPA flush behavior or cross-process timing.

## Authored but NOT executed here

| Suite | Intended coverage |
|---|---|
| `RequestSyncAdvisorTest` | Proxy invocation, ledger-first ordering, checked rollback, invalid signatures, self-invocation limitation |
| `ModelSyncAutoConfigurationTest` | Defaults, custom-bean backoff, disabled mode, missing bindings, invalid bounds |
| `JdbcWorkStoreTest` | H2 ledger transactions, coalescing, leases, quota coordination and persisted config consistency |
| `ReactorSyncWorkerTest` | Dedicated scheduler execution and lazy reactive request behavior |
| `HttpSemanticsTest` | HTTP failure and Retry-After classification |
| `JpaAtomicityTest` | Real Spring/JPA business mutation plus work-generation rollback in the sample |
| `OracleSmokeIT` | Opt-in real Oracle claim/fencing smoke test on an empty dedicated schema |
| `CoreContractsTest` | JUnit wrapper around the same 77 already executed offline scenarios |

Maven was not installed and network resolution/downloads for dependencies were unavailable in the build container. No full `mvn verify`, sample startup, H2 integration run, Oracle run, external API call, vulnerability scan, throughput benchmark or dependency-license audit is reported as successful.

## Reproduction commands

```bash
bash scripts/test-core.sh
python3 scripts/check-architecture.py
java scripts/SourceSyntaxCheck.java .
mvn -B -ntp verify
mvn -B -ntp install
java -jar model-sync-sample/target/model-sync-sample-0.1.0-SNAPSHOT.jar
```

The first three commands reproduce the local checks without downloading Java libraries. The Maven/sample commands require external dependencies and are deployment gates, not historical evidence. Windows has `scripts/test-core.ps1` for the first check.

For Oracle, follow `OPERATIONS.md` and run the opt-in `oracle-it` profile only with a reviewed dedicated test schema. That smoke test is not enough for production acceptance: add multi-process contention, crash, rate-window, outage and large-data tests.

## Acceptance criteria before production

Obtain a green full Maven build and test run on the pinned BOM/JDK. Verify datasource/transaction participation on Oracle and the selected JDBC driver. Prove no source-call retries escape the provider budget. Exercise simultaneous enqueue/claim/local writes and record the lock/SQL plans at realistic scale. Validate strict authority/tenant boundaries, cache serving policy, external identity rules and full-universe coverage assumptions. Review dependencies/security and operational shutdown/recovery. Implement any missing vendor adapters, discovery or delivery workflows actually required by the product.

A successful source build alone does not establish those operational properties. Conversely, the unavailable integration execution does not erase the concrete source and offline tests delivered here; it is a clearly marked remaining verification step.
