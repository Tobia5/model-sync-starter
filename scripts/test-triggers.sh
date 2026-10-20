#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/scripts/test-core.sh"
java -cp "$ROOT/target/offline-core/classes" io.modelsync.testkit.TriggerContracts
