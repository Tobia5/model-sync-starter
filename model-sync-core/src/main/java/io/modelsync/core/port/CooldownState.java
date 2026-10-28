package io.modelsync.core.port;

/** Both outcomes require a committed admission barrier; pending is not materialized completion. */
public enum CooldownState { APPLIED, DURABLE_PENDING }
