#!/usr/bin/env python3
"""Cheap source and POM guards bound to ordinary Maven validate."""
from pathlib import Path
import subprocess
import sys
from run_evidence import begin

ROOT = Path(__file__).resolve().parents[1]
COMMANDS = [
    [sys.executable, 'scripts/check-architecture.py'],
    [sys.executable, 'scripts/check-docs.py'],
    [sys.executable, 'scripts/check-profiles.py'],
    [sys.executable, 'scripts/qualification_target.py'],
    [sys.executable, 'scripts/check-effective-lifecycle.py'],
    [sys.executable, '-m', 'unittest', 'discover', '-s', 'scripts/tests', '-p', 'test_*.py'],
]

for command in COMMANDS:
    result = subprocess.run(command, cwd=ROOT, check=False)
    if result.returncode:
        raise SystemExit(result.returncode)
begin(ROOT)
