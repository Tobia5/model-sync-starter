#!/usr/bin/env python3
"""Validate repository-local documentation links and essential trigger documentation.

This is a static consistency check, not a semantic proof of all example code.
"""
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlsplit

root = Path(__file__).resolve().parents[1]
errors = []
files = [root / 'README.md', *sorted((root / 'docs').glob('*.md'))]
for path in files:
    text = path.read_text(encoding='utf-8')
    for url in re.findall(r'(?<!!)\[[^\]]+\]\(([^\s)]+)(?:\s+"[^"]*")?\)', text):
        parsed = urlsplit(url)
        if parsed.scheme or parsed.netloc or url.startswith('#'):
            continue
        relative = unquote(parsed.path)
        if not relative:
            continue
        target = (path.parent / relative).resolve()
        if not target.is_relative_to(root) or not target.exists():
            errors.append(f'{path.relative_to(root)}: invalid local link {url}')
arc = (root / 'docs/ARC42.md').read_text()
sections = re.findall(r'^## (\d+)\. ', arc, re.M)
if sections != [str(n) for n in range(1, 13)]:
    errors.append(f'arc42 requires sections 1 through 12 in order, got {sections}')
handbook = (root / 'docs/USER-HANDBOOK.md').read_text()
for term in ['ON_DEMAND', 'PERIODIC', 'events-enabled', 'max-items', 'max-duration',
             'STALE_WHILE_REVALIDATE', 'REQUIRE_FRESH', 'DEAD', 'schema', 'WebFlux']:
    if term not in handbook:
        errors.append(f'Handbook missing {term}')
for p in ['docs/REVIEW.md', 'docs/PRODUCTION.md', 'docs/USER-HANDBOOK.md', 'docs/ARC42.md']:
    if not (root / p).is_file():
        errors.append(f'Missing {p}')
if errors:
    print('\n'.join(errors), file=sys.stderr)
    raise SystemExit(1)
print(f'DOCS: {len(files)} Markdown files, local links resolve, all 12 arc42 sections and trigger terms present.')
