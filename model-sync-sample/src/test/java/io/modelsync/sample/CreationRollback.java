package io.modelsync.sample;

/** Deterministic test-owned pre-commit rollback, not a transport/cancellation surrogate. */
final class CreationRollback extends RuntimeException {
  CreationRollback() { super("Test-owned first-creation rollback"); }
}
