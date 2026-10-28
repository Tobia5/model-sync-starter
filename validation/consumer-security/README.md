# Secured independent consumers

These overlays extend the independently packaged MVC and WebFlux hosts. They are
test-only applications, outside the producer reactor. Spring Security7.1.1 comes
from the pinned Boot4.1.1 BOM; no library/core security dependency is introduced.

## Boundary and test authentication

External HTTP calls enter the actual SecurityFilterChain/SecurityWebFilterChain,
then a proxied `ConsumerFacade` with `@PreAuthorize`. The consumer owns the exact
operation/tenant/binding/subject permission policy. Read, refresh and event rights
are independent; unknown targets/operations and missing authority fail closed.
The policy examines identity/grants only and does not read a protected payload.

In-memory synthetic accounts authenticate with framework HTTP Basic and a generated
per-JVM password. All HTTP endpoints are loopback-only. This is a test arrangement,
not an identity provider or production authentication recommendation. CSRF,
BREACH handling and security headers remain enabled; actual Oracle HTTP tests
obtain the framework token/session from `/csrf`, while cheap tests use Spring
Security's MockMvc/WebTestClient support. Credentials and tokens are not retained.

The public gateway, internal `ConsumerModelAccess` and raw Spring event publisher
are trusted in-process capabilities. Code with those references can bypass this
facade deliberately: this is not a sandbox for hostile code in the host JVM.
External service and event ingress must call the secured facade. There is no
unsecured event HTTP endpoint, broker adapter or DEAD replay API in this fixture.

WebFlux method security reads Reactor's SecurityContext, authorizes before access,
then performs blocking gateway work on `modelSyncPersistenceScheduler`; the
detached principal name is observed across that hop. The worker separately holds
a configured known-ID service scope for alpha/beta and the item binding. It checks
that no request principal is inherited. Request SecurityContext is not persisted
in work records or assumed to survive queuing/restart. Revoking an HTTP grant does
not retroactively cancel committed work in this fixture.

## Explicit execution

Keep the original39 staged artifacts and manifest from source551427b unchanged.
Create a new task-owned workspace outside this checkout:

```text
python scripts/consumer_security.py stage <workspace> --artifacts <original-staged-workspace>
python scripts/consumer_security.py prepare <workspace>
```

Preparation runs the cheap security suites independently, resolves dependencies
and the opt-in Failsafe plugin, and records dependency trees/classpaths. No Oracle
test executes. Each new cache starts with only the exact original Model Sync JARs.
Staging rejects changed producer library/platform files and altered artifact hashes.
Fixture SHA/tree and original artifact SHA/tree are separate evidence identities.

After a matching clean producer default verification (not packaging/full), set the
existing invocation-only disposable acknowledgments and approved Maven settings:

```text
python scripts/full-oracle-container.py --consumer-diagnostic <workspace>
```

The security manifest selects only these two consumers' `security-oracle` profile,
using offline independent Maven invocations. The wrapper retains the12GiB small-run
admission,6GiB/4CPU pinned Oracle image, generated schema credentials, separate
schemas, loopback ports and owned cleanup. The full18GiB budget is unchanged.

Required inventory: five MVC/six WebFlux cheap methods and one Oracle journey per
stack. Each Oracle journey requires30 exact negative cases with unchanged protected
read/enqueue/event/owner counters and fresh persisted rows. Four paired committed
outcomes cover two tenants sharing an ID, then changed event/refresh outcomes.
WebFlux also checks32 concurrent HTTP identities across actual scheduler hops.
Existing policy/enlistment/rollback/disabled-mode and physical cleanup controls remain.
Raw partial failures and original exits are retained; selected security PASS is
neither producer full qualification nor a complete security audit.

Real identity-provider integration, TLS/SSRF/secrets, revocation/tenant lifecycle,
arbitrary policies/mappings, broker delivery and operator replay remain outside
this fixture. See QM and the acceptance matrix for measured status.

Framework references: [method authorization](https://docs.spring.io/spring-security/reference/reactive/authorization/method.html),
[MockMvc filter-chain integration](https://docs.spring.io/spring-security/reference/servlet/test/mockmvc/setup.html),
[WebFlux CSRF](https://docs.spring.io/spring-security/reference/reactive/exploits/csrf.html).
