#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/test-core.sh
python3 scripts/check-architecture.py
java scripts/SourceSyntaxCheck.java .
if ! command -v mvn >/dev/null 2>&1; then
    echo 'Maven is required for full verification; offline checks are not a full build.' >&2
    exit 2
fi
mvn -B -ntp verify
