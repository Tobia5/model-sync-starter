# Model Sync contribution rules

Read `docs/PRODUCTION-READINESS.md`, `TODO.md`, `docs/ARC42.md`, and `docs/USER-HANDBOOK.md` before changing behavior.

- Work on a review branch. Preserve unrelated user changes. Do not force-push or auto-merge main.
- Keep core dependency-free. Preserve owner authority, tenant isolation, version checks, lease fencing and local atomicity.
- No owner HTTP while holding database transactions, no managed JPA entities across asynchronous execution lanes, and no hidden owner calls from entity loading.
- Passive visits must not reset source age, bypass cooldown/backoff, create repeated generations under a lease, or revive DEAD work.
- One invocation is not necessarily one physical HTTP call. Test budget enforcement on retries, MDM resolution and bulk lookups explicitly.
- Keep triggers, executors and recurrence independent. Schema changes require versioned migrations and actual Oracle verification.
- Run `bash scripts/test-triggers.sh`, architecture/docs checks, Python guard tests and the complete Maven build. Report exactly which checks ran; syntax parsing is not dependency-linked compilation.
- Oracle, broker and load tests require disposable authorized environments. Never apply test DDL or purge operations to production data.
- Never disable tests or weaken security checks to make CI green. Record blockers and evidence instead.
- Never print credentials or place real subject/tenant identifiers in metric labels. Do not commit connection secrets.
- Do not publish packages without an explicit approved publishing task. The supplied workflow publishes development snapshots only, not stable/production releases.
- Return commits/PR links only after they exist remotely. A local commit, generated patch or task prompt is not a remote commit or launched worker.
