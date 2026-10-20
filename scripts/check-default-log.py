#!/usr/bin/env python3
"""Reject expensive Maven goal executions in a recorded default build log."""
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding='utf-8', errors='replace')
goals = re.findall(r'\[INFO\]\s+---\s+([\w.-]+):[\w.-]+:([\w.-]+)\s+\(', text)
if not goals:
    raise SystemExit('No Maven plugin executions found in default build log')
for plugin, goal in goals:
    if plugin in ('pitest', 'gatling', 'failsafe') or goal in ('mutationCoverage',):
        raise SystemExit(f'Expensive goal executed in default build: {plugin}:{goal}')
print(f'DEFAULT EXECUTION AUDIT PASS: {len(goals)} recorded plugin goals, none expensive')
