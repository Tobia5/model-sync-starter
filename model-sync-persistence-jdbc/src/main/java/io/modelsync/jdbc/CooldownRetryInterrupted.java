package io.modelsync.jdbc;

/** Only an interrupted retry pause, never a generic/uncertain transaction completion failure. */
final class CooldownRetryInterrupted extends java.util.concurrent.CancellationException {
  CooldownRetryInterrupted(){super("Cooldown retry interrupted");}
}
