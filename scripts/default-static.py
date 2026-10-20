#!/usr/bin/env python3
"""Cheap source and POM guards bound to ordinary Maven validate."""
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
COMMANDS = [
    [sys.executable, 'scripts/check-architecture.py'],
    [sys.executable, 'scripts/check-docs.py'],
    [sys.executable, 'scripts/check-profiles.py'],
    [sys.executable, 'scripts/check-effective-lifecycle.py'],
    [sys.executable, '-m', 'unittest', 'discover', '-s', 'scripts/tests', '-p', 'test_*.py'],
]

for command in COMMANDS:
    result = subprocess.run(command, cwd=ROOT, check=False)
    if result.returncode:
        raise SystemExit(result.returncode)
(ROOT / 'target/qm').mkdir(parents=True, exist_ok=True)
(ROOT / 'target/qm/build-start.txt').write_text(str(time.time()), encoding='utf-8')
