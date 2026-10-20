# Model Sync quality evidence

Interim local evidence for tested source `dcc2fa4c05b81cace006f24a87a4e39fe2fa915f` (tree `a03f42a4d936d8cd5468487257f2e91883427dc3`), recorded 2026-10-20. A later documentation-only update will record the final source check.

| Attempt | Result |
|---|---|
| `./mvnw -B -ntp clean verify` | BLOCKED, exit 1 at Reactor dependency resolution (module 8/15). Earlier modules reported 30 JUnit tests, 0 failed/skipped. |
| `python scripts/full-preflight.py` | BLOCKED, exit 2; disposable Oracle environment inputs absent. Full `clean verify` NOT_RUN. |

The configured Nexus host `kubernetes-dev` was unresolved. PIT, Gatling, Oracle and the complete compiled-output architecture scan are NOT_RUN or BLOCKED. No release qualification or package publication is claimed.
