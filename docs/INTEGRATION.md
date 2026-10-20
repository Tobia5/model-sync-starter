# Integration guide

## 1. Source DTO versus JPA entity

Use an immutable source-owned DTO as the type argument to `ModelBinding<T>`. Map it to your existing JPA entity through `JpaProjectionMapping<T,E>`:

```java
public interface JpaProjectionMapping<T, E> {
    Optional<E> find(EntityManager em, SubjectKey key, LockModeType lockMode);
    Projection<T> snapshot(E entity);
    E create(SubjectKey key, T value, Instant expiresAt);
    void update(E entity, T value, Instant expiresAt);
}
```

The actual supplied interface is in `model-sync-persistence-jpa`. `find` must include tenant scope, including when using a hashed primary key. `snapshot` must return immutable mapped data and a reliable local revision, normally `@Version`. `update` must preserve fields that belong to other owners. The sample mapping is small and demonstrates these rules without requiring extra annotations on your entity.

The generic JPA adapter handles transaction requirements, locking/version checks and flush. It does not reflectively copy every property. There is no default JSON hash based on arbitrary Hibernate proxies, lazy associations or unstable field order.

Fingerprint only the fields being reconciled, with deterministic normalization appropriate to those fields. Do not lowercase legal names, round amounts or rewrite addresses merely to suppress mismatches. Include a mapping version in the fingerprint, as the sample does. Keep the ownership/normalization policy under application review.

## 2. Blocking REST owner

`RestClientOwner<T>` accepts an already configured Spring `RestClient`, the DTO class and a `Function<ReadRequest, URI>`. It handles response status/ETag translation; it does not know your authorization, tenancy or source endpoint contract.

The actual sample implementation is `RestOwnerConfiguration.java`. It requires an HTTPS base URL, uses a JDK transport with explicit connection/read timeouts and no automatic redirect following, and constructs a tenant-scoped URI. The path `/tenants/{tenant}/customers/{id}` is an example contract, not an IBM or Vista endpoint.

A custom source is equally valid:

```java
@Bean
@DataOwner("customer")
BlockingOwner<Customer> customerOwner(ApprovedCustomerClient client) {
    return request -> client.fetchSnapshot(request.key(), request.validator());
}
```

`ApprovedCustomerClient` in this fragment means your application client returning `FetchResult<Customer>`; it is not a class supplied by the library. Existing Spring HTTP service interfaces can be wrapped here. The library deliberately reuses standard Spring clients rather than introducing another HTTP annotation proxy (S6).

404 becomes observed absence and never an automatic local delete. Authentication/authorization/protocol errors are not treated as absence. Provider-specific status semantics should be translated by a custom adapter where the standard mapping is inappropriate.

## 3. Native WebClient owner

Add the WebClient starter and register an `AsyncOwner<T>`:

```java
@Bean
@DataOwner("customer")
AsyncOwner<Customer> asyncCustomerOwner(WebClient approvedClient) {
    return new WebClientOwner<>(approvedClient, Customer.class, request ->
        UriComponentsBuilder.fromUriString("https://owner.example.invalid")
            .pathSegment("tenants", request.key().tenant(),
                         "customers", request.key().id())
            .build().encode().toUri());
}
```

The `.invalid` host is deliberately non-routable documentation, not a working service. Replace it with your approved configured base URL. Configure transport connection/read deadlines, authentication, request/response size limits and redirect/retry behavior on `approvedClient`; a Reactor publisher timeout does not replace every transport safeguard.

Bind with `.asyncOwner("external-customer-system", asyncCustomerOwner)` instead of `.owner(...)`. Native async results are represented as JDK `CompletionStage` at the core boundary. `ReactiveOwners.from` also adapts an application function returning `Mono<FetchResult<T>>`.

The supplied WebClient adapter consumes/releases response bodies within the exchange lifecycle. It classifies relative Retry-After values without querying the database clock on the event loop. See S6/S12 for Spring client contracts.

## 4. Reactive application facade

```java
ReactiveModelSync<Customer> customers = reactiveGateway.model(Customer.class);
Mono<String> ticket = customers.request("tenant-a", "42", Priority.URGENT, true);
Mono<Projection<Customer>> local = customers.local("tenant-a", "42");
```

These operations are lazy until subscribed. The request publisher represents durable enqueue completion, not the final external sync. Do not synchronously call the blocking `ModelSync` API from an event-loop handler. Native HTTP does not make JPA calls nonblocking.

The reactive facade currently supplies `request`, `status` and bounded-staleness `local`. The synchronous facade additionally exposes `inspect` and `freshLocal`. An application can bridge those latter methods on the supplied persistence scheduler where needed; no misleading reactive overload is declared in this version.

## 5. Multiple roles and multiple bindings

A model class can participate in a `customer-cache` binding and a `customer-replica` binding. The source DTO type can be the same; the binding IDs, projection stores, freshness and repair policies are explicit. Request each through `gateway.model(bindingId, Customer.class)`.

Do not point two independent bindings at the same physical projection rows with conflicting ownership or expiry semantics. The starter cannot infer that two different mapper beans address the same table. Shared storage must have a deliberate single-writer/field-ownership strategy.

For an analytical target, use dataset contracts rather than pretending the remote destination is a local `ProjectionStore`. The local CAS port is not a hook for external writes inside the transaction.

## 6. IBM MDM integration seam

Register an `IdentityResolver` for your deployed IBM MDM API and authoritative cross-reference rules. Its `Resolution` provides status, canonical ID, aliases and mapping version. It is intentionally not invoked automatically by `ModelBinding`: source IDs and identity rules differ between integrations.

The application can pre-resolve mappings during subject registration, react to MDM mapping events by enqueuing affected routes, or implement a separately budgeted mapping stage. Ambiguous identities require quarantine/manual handling. Mapping failures are not permission to assign a new identity or merge customers.

No IBM classes, credentials or guessed endpoints are included. A custom adapter that performs multiple requests must gate every request and manage their deadlines. The starter's single owner-fetch permit is not a blanket allowance for an arbitrary chain of REST calls.

## 7. Boundary owner: catalog versus inventory

```java
List<ModelBinding<?>> configured = gateway.boundaryModels("cards");
```

This is local registered metadata. To access the source's inventory, inject a qualified `BoundaryInventory` bean and call its explicit paged API. The provider supplies an external catalog; the application still decides which model bindings are supported and approved.

The initial package does not contain a checkpoint table or importer. Design an importer that persists a stable snapshot/cursor with durable page enqueue. A cursor should be advanced in the same transaction as accepting its records. Every enumeration request needs admission. A recurring local age scan alone cannot find arbitrary owner-only records.

## 8. Vista/DWH manifest reader

`VistaPort` and `DwhPort` extend `DatasetPort.Reader`; register your implementation as a bean. A database-backed manifest view can use the supplied `JdbcDatasetReader`:

```java
@Bean
DwhPort dwhManifestReader(NamedParameterJdbcTemplate dwhJdbc) {
    DatasetPort.Reader delegate = new JdbcDatasetReader(
        dwhJdbc,
        """
        select dataset_id, tenant_id, snapshot_id, partition_id,
               mapping_version, grain, row_count, unresolved_count,
               content_digest, gross_amount
        from approved_dataset_manifest
        where dataset_id = :dataset
          and tenant_id = :tenant
          and snapshot_id = :snapshot
          and partition_id = :partition
          and mapping_version = :mappingVersion
          and grain = :grain
        """,
        Map.of("grossAmount", "gross_amount"));
    return delegate::inspect;
}
```

This SQL is a template for an application-owned manifest view, not a shipped table. Bind data values; never accept SQL text/table/column identifiers from end users. Use a read-only database credential and finite query deadlines appropriate to the warehouse.

```java
DatasetPort.Comparison result = DatasetPort.compare(
    expectedReader.inspect(slice), dwhReader.inspect(slice));
```

Do not compare snapshots from different cutoffs or mapping versions. Unresolved/excluded data must remain part of coverage evidence. A successful upload is not a verified warehouse projection. Delivery receipts and manifest reads have distinct semantics.

## 9. Replacing defaults

Define your own `WorkStore`, `LocalTransactions`, `QuotaGate`, `SyncOptions`, `WeightedPriorities`, `SyncCoordinator`, `SyncGateway`, `ReactiveSyncGateway` or `ReactorSyncWorker` bean to replace that infrastructure role. Supply a `ModelRegistry` only when you deliberately want to own registration yourself.

The default `Clock` bean is named `modelSyncClock`. Scheduler names are `modelSyncPersistenceScheduler` and `modelSyncOwnerScheduler`; replacements must preserve their thread-isolation contract and lifecycle handling. Custom defaults should use concrete bean return types so auto-configuration can evaluate their presence reliably (S2).

Bindings are not automatically replaced by type. Explicit bean registration is intentional: adding an address owner should not disable a customer's default or select a different route by accident. An invalid/missing binding or quota fails startup rather than silently using a permissive fallback.
