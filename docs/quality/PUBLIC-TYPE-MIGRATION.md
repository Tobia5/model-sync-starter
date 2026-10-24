# Public Java type migration for 0.2.0-SNAPSHOT

This unreleased minor snapshot line deliberately breaks **source and binary compatibility** with 0.1.0-SNAPSHOT. Recompile consumers and update imports; retained nested aliases would violate the flat-type rule. No released 0.1.x artifact is overwritten. Constructor/method signatures otherwise remain subject to consumer compilation checks, which are blocked until the complete dependency reactor runs.

| Former `io.modelsync.core` type | New type |
|---|---|
| `SubjectKey`, `WorkKey`, `Projection`, `SyncState`, `FreshnessPolicy`, `Digests` | `io.modelsync.core.domain` with the same simple name |
| `FreshnessPolicy.Age` | `io.modelsync.core.domain.FreshnessAge` |
| `ModelBinding`, `ModelRegistry`, `ModelSync`, `SyncAccess`, `SyncCoordinator`, `SyncGateway`, `SyncJob`, `SyncTriggers`, `PreparedSync` | `io.modelsync.core.application` with the same simple name |
| `ModelBinding.Builder<T>` | `io.modelsync.core.application.ModelBindingBuilder<T>`; `ModelBinding.builder(...)` remains |
| `ModelSync.LocalView<T>` | `io.modelsync.core.LocalModelView<T>` |
| `OwnerPort`, `BlockingOwner`, `AsyncOwner`, `ProjectionStore`, `WorkStore`, `QuotaGate`, `LocalTransactions`, `BoundaryInventory`, `IdentityResolver`, `DatasetPort.Reader`, `DatasetPort.Sink`, `VistaPort`, `DwhPort` | `io.modelsync.core.port` with the same simple name except `DatasetReader` and `DatasetSink` |
| `FetchResult.Status` | `io.modelsync.core.FetchStatus` |
| `QuotaGate.Admission` | `io.modelsync.core.QuotaAdmission` |
| `SyncJob.Result`, `SyncJob.StopReason` | `io.modelsync.core.SyncJobResult`, `io.modelsync.core.SyncJobStopReason` |
| `BoundaryInventory.ModelDescriptor`, `BoundaryInventory.Page` | `io.modelsync.core.BoundaryModelDescriptor`, `io.modelsync.core.BoundaryPage` |
| `DatasetPort.Slice`, `.Manifest`, `.Receipt`, `.Receipt.Stage`, `.Comparison` | `io.modelsync.core.DatasetSlice`, `DatasetManifest`, `DatasetReceipt`, `DatasetReceiptStage`, `DatasetComparison` |
| `IdentityResolver.Resolution`, `.Resolution.Status` | `io.modelsync.core.IdentityResolution`, `IdentityResolutionStatus` |
| `SyncExceptions.LeaseLost`, `.RetryLater`, `.RateLimited`, `.RateLimitedAfter`, `.Permanent` | `io.modelsync.core` top-level types with those simple names |
| `io.modelsync.rest.HttpSemantics` | `io.modelsync.core.http.HttpSemantics` |

Internal/test fixture extractions include `JdbcWorkRow`, `MemoryWorkCell`, `MemoryWorkSnapshot`, `MemoryQuota`, `LegacyWorkStore`, `TriggerFixture`, `TriggerJobProperties`, `RequestSyncInvocationFailure`, `RequestSyncMethodPointcut`, and top-level AOP test services. Their names are not a public compatibility promise.
