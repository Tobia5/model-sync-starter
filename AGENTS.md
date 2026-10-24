# Model Sync contribution rules

Read `docs/PRODUCTION-READINESS.md`, `TODO.md`, `docs/ARC42.md`, `docs/USER-HANDBOOK.md`, and `docs/quality/TEST-PROFILES.md` before changing behavior. Work on a review branch, preserve unrelated changes, and do not force-push or merge main.

- Keep core free of framework dependencies. Preserve owner authority, tenant isolation, local revision checks, lease fencing, quota accounting, and local transaction atomicity.
- Never call an owner while holding a database transaction or lock. Do not move managed JPA entities across asynchronous lanes or hide owner calls in entity loading.
- Passive visits must preserve source age, retry backoff, cooldown, leased generations, and DEAD quarantine. A logical invocation may involve multiple physical HTTP calls; count retries, MDM resolution, and bulk lookups.
- Keep trigger, execution, and recurrence choices independent. Schema changes need versioned migrations and real Oracle verification in a disposable authorized schema.
- Keep every hand-written named class, record, interface, enum, and annotation in its own Java source file, including tests and testkit. Do not use anonymous/local classes or JUnit `@Nested`.
- Run `bash scripts/test-triggers.sh`, `python scripts/check-architecture.py`, `python scripts/check-docs.py`, `python -m unittest discover -s scripts/tests -p 'test_*.py'`, and `./mvnw -B -ntp clean verify`. Syntax parsing is not dependency-linked compilation.
- The explicit release qualification is `./mvnw -B -ntp -Pfull clean verify` in an approved disposable environment. It adds PIT, Oracle migration/concurrency/scale, and Gatling. Missing infrastructure is BLOCKED, not PASS. Never skip required tests or weaken the score gate.
- Never apply test DDL or purge operations to production data. Do not print credentials, include real tenant/subject identifiers in metric labels, or commit connection secrets.
- Do not publish packages without an explicit approved task. Development snapshot workflows are not stable release workflows.
- Record actual commands, source SHA/tree, exit codes, reports, and blockers in `QM.md`. A local commit or patch is not a remote commit or PR.
