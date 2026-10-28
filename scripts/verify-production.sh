#!/usr/bin/env bash
# Automated gates only; Oracle/security/operational sign-off remains separate.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
bash scripts/test-triggers.sh
python3 scripts/check-architecture.py
python3 scripts/check-docs.py
java scripts/SourceSyntaxCheck.java .
if ! command -v mvn >/dev/null 2>&1; then
  echo 'BLOCKED: Maven is unavailable. Framework/Oracle production qualification has NOT passed.' >&2
  exit 2
fi
mvn -B -ntp verify
echo 'Automated default lanes passed. Oracle migration/concurrency, security and operations approval are still required.'
