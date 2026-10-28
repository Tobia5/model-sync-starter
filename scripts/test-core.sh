#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/target/offline-core"
mkdir -p "$OUT"
if command -v cygpath >/dev/null 2>&1; then
  find "$ROOT/model-sync-core/src/main/java" "$ROOT/model-sync-testkit/src/main/java" -name '*.java' | sort |
    while IFS= read -r source; do printf '"%s"\n' "$(cygpath -m "$source")"; done > "$OUT/sources.txt"
  CLASSES="$(cygpath -w "$OUT")\\classes"
  SOURCES="$(cygpath -w "$OUT")\\sources.txt"
else
  find "$ROOT/model-sync-core/src/main/java" "$ROOT/model-sync-testkit/src/main/java" -name '*.java' | sort | sed 's/^/"/;s/$/"/' > "$OUT/sources.txt"
  CLASSES="$OUT/classes"
  SOURCES="$OUT/sources.txt"
fi
javac --release 21 -encoding UTF-8 -d "$CLASSES" @"$SOURCES"
java -cp "$CLASSES" io.modelsync.testkit.CoreContracts
