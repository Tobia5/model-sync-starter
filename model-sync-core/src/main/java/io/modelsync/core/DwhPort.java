package io.modelsync.core;

/**
 * Typed dataset-reading extension point for data warehouse integrations.
 *
 * <p>No vendor protocol, transport, or authentication mechanism is assumed by the core library.
 */
@FunctionalInterface
public interface DwhPort extends DatasetPort.Reader {}
