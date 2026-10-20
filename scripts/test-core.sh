#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/target/offline-core"
mkdir -p "$OUT"
find "$ROOT/model-sync-core/src/main/java" "$ROOT/model-sync-testkit/src/main/java" -name '*.java' | sort | sed 's/^/"/;s/$/"/' > "$OUT/sources.txt"
javac --release 21 -encoding UTF-8 -d "$OUT/classes" @"$OUT/sources.txt"
java -cp "$OUT/classes" io.modelsync.testkit.CoreContracts
