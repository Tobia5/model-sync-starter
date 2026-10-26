# Independent local artifact consumers

These templates are deliberately outside the producer Maven reactor. `common`
plus either `mvc` or `webflux` becomes a separate consumer project in a task-owned
directory outside this checkout. Each consumer has its own initially empty Maven
repository, seeded only with the exact staged Model Sync artifacts. Public
dependencies use the approved project Central settings. No registry publication
or authentication is exercised.

The consumer owns its DTO, entity, mapping, binding, controller and loopback HTTP
owner. It uses Boot's discovery of the packaged `AutoConfiguration.imports`; it
does not import the producer sample or testkit. Oracle execution is explicit:

```text
mvnw -B -ntp -s .mvn/qualification-central.settings.xml -Dmaven.repo.local=<task-repo> -Plibrary-artifacts clean install
python scripts/consumer_artifacts.py stage <external-workspace> --repository <task-repo>
python scripts/consumer_artifacts.py prepare <external-workspace>
python scripts/full-oracle-container.py --consumer-diagnostic <external-workspace>
```

The last command requires the existing disposable acknowledgments, reviewed
settings and one bounded 12 GiB resource admission. It retains the pinned Oracle
image, 6 GiB/4 CPU caps, generated credentials, isolated schemas and loopback
ports. Consumer JVMs run sequentially with 512 MiB heap/128 MiB direct-memory caps.
The full lane still requires 18 GiB and is not selected by this command.

Preparation compiles and resolves dependencies only. Each separate offline
consumer `verify` starts its actual servlet/reactive server, checks effective
adapter policies/enlistment and a named clock replacement, then observes three
committed values A/B/C through request/request/event. It also starts disabled
mode and checks drain, pools and owned scheduler threads. A successful HTTP
response alone is not reconciliation evidence.

This single-table mapping consumes per-operation NOWAIT settings. Its journey
does not repeat the producer's contention, crash, load or full qualification,
and does not establish a bounded-wait promise for arbitrary consumer mappings.
See the current QM report for actual execution status; templates are not results.
