#!/usr/bin/env python3
"""Summarize this run's raw verification evidence without changing build exit status."""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timezone
import json
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def load_checker(name: str):
    spec = spec_from_file_location(name.replace('-', '_'), ROOT / 'scripts' / (name + '.py'))
    module = module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def command(*args: str) -> str:
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True, check=False)
    return (result.stdout or result.stderr).strip().splitlines()[0] if result.returncode == 0 else 'UNAVAILABLE'


def xml_suites(directory: Path, started: float | None) -> dict:
    result = Counter()
    files = 0
    for path in directory.glob('TEST-*.xml'):
        if started is not None and path.stat().st_mtime < started:
            result['stale_files'] += 1
            continue
        try:
            doc = ET.parse(path).getroot()
            suites = [doc] if doc.tag == 'testsuite' else list(doc.iter('testsuite'))
            for suite in suites:
                for key in ('tests', 'errors', 'failures', 'skipped'):
                    result[key] += int(suite.get(key, 0))
            files += 1
        except (OSError, ET.ParseError, ValueError):
            result['malformed'] += 1
    return {'files': files, **dict(result)}


def collect() -> dict:
    modules = {}
    marker = ROOT / 'target/qm/build-start.txt'
    started = float(marker.read_text(encoding='utf-8')) if marker.is_file() else None
    for module in sorted(ROOT.glob('model-sync-*')):
        if not (module / 'pom.xml').is_file():
            continue
        modules[module.name] = {
            'surefire': xml_suites(module / 'target/surefire-reports', started),
            'failsafe': xml_suites(module / 'target/failsafe-reports', started),
        }
    mutation = {'status': 'NOT_RUN'}
    pit = ROOT / 'model-sync-testkit/target/pit-reports/mutations.xml'
    if pit.is_file():
        try:
            counts = Counter(item.get('status', 'UNKNOWN') for item in ET.parse(pit).getroot().iter('mutation'))
            total = sum(counts.values())
            mutation = {'status': 'MEASURED', 'counts': dict(counts), 'generated': total,
                        'killed_over_generated_percent': round(100 * counts['KILLED'] / total, 2) if total else None,
                        'html': (pit.parent / 'index.html').is_file()}
        except ET.ParseError:
            mutation = {'status': 'MALFORMED'}
    load = {}
    for path in (ROOT / 'model-sync-performance/target/qm').glob('load-*.json'):
        try:
            load[path.stem.removeprefix('load-')] = json.loads(path.read_text(encoding='utf-8'))
        except (OSError, json.JSONDecodeError):
            load[path.stem] = {'status': 'MALFORMED'}
    oracle_scale = ROOT / 'model-sync-persistence-jdbc/target/qm/oracle-scale.json'
    try:
        scale = json.loads(oracle_scale.read_text(encoding='utf-8')) if oracle_scale.is_file() else None
    except (OSError, json.JSONDecodeError):
        scale = {'status': 'MALFORMED'}
    return {'modules': modules, 'mutation': mutation, 'gatling': load, 'oracle_scale': scale}


def markdown(data: dict) -> str:
    runs = data['runs']
    evidence = data['evidence']
    unit = Counter()
    integration = Counter()
    for module in evidence['modules'].values():
        for key in ('tests', 'errors', 'failures', 'skipped'):
            unit[key] += module['surefire'].get(key, 0)
            integration[key] += module['failsafe'].get(key, 0)
    lines = [
        '# Model Sync quality evidence', '',
        f"Generated: {data['generated_utc']}",
        f"Tested source commit: `{data['source_sha']}`; tree: `{data['source_tree']}`.",
        f"Working tree dirty at report generation: {str(data['working_tree_dirty']).lower()}.",
        f"Environment: {data['environment']['java']}; {data['environment']['maven']}; {data['environment']['python']}.",
        '', '## Commands and outcome', '',
        '| Lane | Exact command | Exit | Status |', '|---|---|---:|---|',
    ]
    for lane in ('default', 'full'):
        item = runs[lane]
        lines.append(f"| {lane} | `{item['command']}` | {item['exit'] if item['exit'] is not None else '—'} | {item['status']} |")
    lines += ['', '## Measured evidence', '',
              f"Surefire: {unit['tests']} tests, {unit['failures']} failures, {unit['errors']} errors, {unit['skipped']} skipped across {sum(v['surefire']['files'] for v in evidence['modules'].values())} XML files.",
              f"Failsafe: {integration['tests']} tests, {integration['failures']} failures, {integration['errors']} errors, {integration['skipped']} skipped across {sum(v['failsafe']['files'] for v in evidence['modules'].values())} XML files."]
    pit = evidence['mutation']
    lines.append('PIT: ' + (f"{pit['generated']} mutations; {pit['counts']}; killed/generated {pit['killed_over_generated_percent']}%." if pit['status'] == 'MEASURED' else pit['status'] + '.'))
    lines.append('Gatling: ' + (', '.join(sorted(evidence['gatling'])) + ' local scenario evidence present.' if evidence['gatling'] else 'NOT_RUN; no latency percentiles or completed-throughput measurement.'))
    lines.append('Oracle scale: ' + (json.dumps(evidence['oracle_scale'], sort_keys=True) if evidence['oracle_scale'] else 'NOT_RUN.'))
    lines += ['', '## Scope and blockers', '',
              'A default PASS does not qualify full release behavior. Full requires nonzero Oracle migration/concurrency/scale suites, selected PIT scope at 85% killed/generated, and all six Gatling reports. See `scripts/check-full-reports.py`.',
              'The local Gatling harness uses an in-memory test store; its data is not Oracle capacity evidence.',
              'No package publication or production deployment is authorized by this report.', '']
    for blocker in data['blockers']:
        lines.append(f'- {blocker}')
    return '\n'.join(lines) + '\n'


def main() -> None:
    parser = argparse.ArgumentParser()
    for lane in ('default', 'full'):
        parser.add_argument(f'--{lane}-command', default='NOT_RUN')
        parser.add_argument(f'--{lane}-exit', type=int)
        parser.add_argument(f'--{lane}-status', choices=('PASS', 'FAILED', 'BLOCKED', 'NOT_RUN'), default='NOT_RUN')
    parser.add_argument('--blocker', action='append', default=[])
    args = parser.parse_args()
    runs = {}
    for lane in ('default', 'full'):
        status = getattr(args, lane + '_status')
        code = getattr(args, lane + '_exit')
        if status == 'PASS' and code != 0:
            raise SystemExit(f'{lane} PASS requires exit code zero')
        if status == 'PASS':
            checker = 'check-test-reports' if lane == 'default' else 'check-full-reports'
            try:
                load_checker(checker).check(ROOT)
            except (ValueError, OSError, ET.ParseError, json.JSONDecodeError) as error:
                raise SystemExit(f'{lane} PASS lacks required raw evidence: {error}') from error
        runs[lane] = {'command': getattr(args, lane + '_command'), 'exit': code, 'status': status}
    sha = command('git', 'rev-parse', 'HEAD')
    tree = command('git', 'rev-parse', 'HEAD^{tree}')
    dirty = bool(subprocess.run(['git', 'status', '--porcelain'], cwd=ROOT,
                                capture_output=True, text=True, check=False).stdout.strip())
    data = {'generated_utc': datetime.now(timezone.utc).isoformat(), 'source_sha': sha,
            'source_tree': tree, 'working_tree_dirty': dirty,
            'environment': {'java': command('java', '-version'), 'maven': command('mvn', '-version'),
                            'python': command('python', '--version')},
            'runs': runs, 'evidence': collect(), 'blockers': args.blocker}
    out = ROOT / 'target/qm'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'evidence.json').write_text(json.dumps(data, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    report = markdown(data)
    (out / 'QM.md').write_text(report, encoding='utf-8')
    print(report)
    import os
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as summary:
            summary.write('\n'.join(report.splitlines()[:14]) + '\n')


if __name__ == '__main__':
    main()
