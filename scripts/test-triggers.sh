#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/scripts/test-core.sh"
if command -v cygpath >/dev/null 2>&1; then
  CLASSES="$(cygpath -w "$ROOT/target/offline-core")\\classes"
else
  CLASSES="$ROOT/target/offline-core/classes"
fi
java -cp "$CLASSES" io.modelsync.testkit.TriggerContracts
