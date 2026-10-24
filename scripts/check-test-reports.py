#!/usr/bin/env python3
"""Require nonempty, unskipped Surefire results for each module with unit-test classes.

This does NOT attest that Oracle *IT tests ran. Those require the explicit full profile.
Use after Maven clean verify, never against old target directories.
"""
from __future__ import annotations
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
from run_evidence import current, fresh, reactor_modules

PATTERN = re.compile(r'(?:Test.*|.*Test|.*Tests|.*TestCase)\.java$')

def check(root: Path) -> tuple[int, int]:
    count = modules = 0
    current(root)
    for module in reactor_modules(root):
        sources = module / 'src/test/java'
        expected = {p.stem for p in sources.rglob('*.java') if PATTERN.fullmatch(p.name)}
        if not expected:
            continue
        reports = sorted((module / 'target/surefire-reports').glob('TEST-*.xml'))
        if not reports:
            raise ValueError(f'Missing Surefire reports: {module.name}')
        seen: set[str] = set()
        module_count = 0
        for report in reports:
            fresh(report, root)
            document = ET.parse(report).getroot()
            suites = [document] if document.tag == 'testsuite' else list(document.iter('testsuite'))
            for suite in suites:
                tests = int(suite.get('tests', '0'))
                bad = sum(int(suite.get(k, '0')) for k in ('failures', 'errors', 'skipped'))
                if bad:
                    raise ValueError(f'Failures/errors/skips in {report.relative_to(root)}')
                if tests:
                    seen.add((suite.get('name') or '').rsplit('.', 1)[-1].split('$')[0])
                module_count += tests
        if not module_count or expected - seen:
            raise ValueError(f'Missing or empty test suites: {module.name}: {sorted(expected - seen)}')
        modules += 1
        count += module_count
    if not modules or not count:
        raise ValueError('No executed unit test suites found')
    return modules, count

def main() -> int:
    try:
        modules, count = check(Path(__file__).resolve().parents[1])
    except (ValueError, OSError, ET.ParseError) as error:
        print(f'BLOCKED: {error}', file=sys.stderr)
        return 1
    print(f'PASS: {count} reported tests in {modules} test-bearing modules; no failures/errors/skips.')
    print('Oracle integration, migration, and load qualification are separate gates.')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
