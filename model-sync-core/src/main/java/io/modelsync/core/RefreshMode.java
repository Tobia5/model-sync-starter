package io.modelsync.core;

/** Successful-work recurrence. Failures still use durable retry in either mode. */
public enum RefreshMode {
  /** Known subjects are due again at the binding's refreshAfter threshold. */
  PERIODIC,
  /** Preserve evidence but park successful work until a new explicit request arrives. */
  ON_DEMAND
}
