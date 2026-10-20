#!/usr/bin/env python3
"""Summarize this run's raw verification evidence without changing build exit status."""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timezone
import json
import hashlib
import os
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location
import re
import subprocess
import xml.etree.ElementTree as ET
from run_evidence import current, fresh, reactor_modules

ROOT = Path(__file__).resolve().parents[1]


def load_checker(name: str):
    spec = spec_from_file_location(name.replace('-', '_'), ROOT / 'scripts' / (name + '.py'))
    module = module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def command(*args: str) -> str:
    try:
        result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True, check=False)
    except OSError:
        return 'UNAVAILABLE'
    return (result.stdout or result.stderr).strip().splitlines()[0] if result.returncode == 0 else 'UNAVAILABLE'


def xml_suites(directory: Path, root: Path) -> dict:
    result = Counter()
    files = 0
    rejected = []
    named = []
    for path in directory.glob('TEST-*.xml'):
        try:
            fresh(path, root)
            doc = ET.parse(path).getroot()
            suites = [doc] if doc.tag == 'testsuite' else list(doc.iter('testsuite'))
            if not suites:
                raise ValueError('No JUnit test suite')
            counts = Counter()
            for suite in suites:
                for key in ('tests', 'errors', 'failures', 'skipped'):
                    value = int(suite.attrib[key])
                    if value < 0:
                        raise ValueError(f'Negative {key}')
                    counts[key] += value
            result.update(counts)
            named.extend({'name': suite.get('name', path.stem),
                          **{key: int(suite.attrib[key]) for key in ('tests', 'errors', 'failures', 'skipped')}}
                         for suite in suites)
            files += 1
        except (OSError, ET.ParseError, ValueError, KeyError) as error:
            rejected.append(f'{path.relative_to(root)}: {error}')
    status = ('REJECTED' if rejected else 'NOT_RUN' if not files else
              'FAILED' if not result['tests'] or result['errors'] or result['failures'] or result['skipped'] else 'PASS')
    return {'status': status, 'files': files, **dict(result), 'suites': named, 'rejected': rejected}


def collect(full: bool, root: Path = ROOT) -> dict:
    empty = {'modules': {}, 'mutation': {'status': 'NOT_RUN'}, 'gatling': {},
             'oracle_scale': None, 'oracle_workload': None, 'rejected': []}
    try:
        run = current(root)
    except ValueError as error:
        return {**empty, 'attribution': 'NONE', 'rejected': [str(error)]}
    log_path = root / 'target/qm' / ('full-build.log' if full else 'default-build.log')
    log = ''
    log_rejected = []
    if log_path.is_file():
        try:
            fresh(log_path, root)
            log = log_path.read_text(encoding='utf-8', errors='replace')
        except (OSError, ValueError) as error:
            log_rejected.append(str(error))
    modules = {}
    for module in reactor_modules(root, full):
        modules[module.name] = {
            'surefire': xml_suites(module / 'target/surefire-reports', root),
            'failsafe': xml_suites(module / 'target/failsafe-reports', root) if full else {'status': 'NOT_RUN', 'files': 0},
        }
    if full and log and 'model-sync-persistence-jdbc' in modules:
        oracle = modules['model-sync-persistence-jdbc']['failsafe']
        reported = {suite['name'].rsplit('.', 1)[-1] for suite in oracle['suites']}
        for name in ('OracleSmokeIT', 'OracleConcurrencyIT', 'OracleMigrationIT', 'OracleScaleIT', 'OracleCapacityIT'):
            if name not in reported and re.search(r'\bRunning\s+(?:[\w.]+\.)?' + name + r'\b', log):
                oracle['suites'].append({'name': name, 'tests': 0, 'failures': 0, 'errors': 0,
                                         'skipped': 0, 'status': 'FAILED', 'reason': 'executed without XML report'})
                oracle['status'] = 'FAILED'
    mutation = {'status': 'NOT_RUN'}
    pit = root / 'model-sync-testkit/target/pit-reports/mutations.xml'
    if full and pit.is_file():
        try:
            fresh(pit, root)
            counts = Counter(item.get('status', 'UNKNOWN') for item in ET.parse(pit).getroot().iter('mutation'))
            total = sum(counts.values())
            html = pit.parent / 'index.html'
            if html.is_file():
                fresh(html, root)
            score = 100 * counts['KILLED'] / total if total else None
            mutation = {'status': 'PASS' if score is not None and score >= 85 and
                        not any(counts[x] for x in ('TIMED_OUT', 'RUN_ERROR', 'UNKNOWN')) and html.is_file()
                        else 'FAILED', 'counts': dict(counts), 'generated': total,
                        'killed_over_generated_percent': round(100 * counts['KILLED'] / total, 2) if total else None,
                        'html': html.is_file()}
        except (OSError, ET.ParseError, ValueError) as error:
            mutation = {'status': 'REJECTED', 'reason': str(error)}
    elif full and re.search(r'---\s+pitest:[^\s]+:mutationCoverage\b', log):
        mutation = {'status': 'FAILED', 'reason': 'PIT goal executed without XML report'}
    load = {}
    for path in ((root / 'model-sync-performance/target/qm').glob('load-*.json') if full else ()):
        try:
            fresh(path, root)
            name = path.stem.removeprefix('load-')
            value = json.loads(path.read_text(encoding='utf-8'))
            if not isinstance(value, dict) or value.get('scenario') != name or value.get('status') not in ('PASS', 'FAILED'):
                raise ValueError('Malformed or mismatched Gatling evidence')
            load[name] = value
        except (OSError, json.JSONDecodeError, ValueError) as error:
            load[path.stem.removeprefix('load-')] = {'status': 'REJECTED', 'reason': str(error)}
    if full:
        executions = {'fresh-visits': 'fresh', 'hot-key': 'hot', 'unsafe-values': 'unsafe',
                      'event-job-visit': 'event', 'cooldown-recovery': 'cooldown', 'leases-ledger': 'leases'}
        for execution, name in executions.items():
            if name not in load and re.search(r'---\s+gatling:[^\s]+:test\s+\(' + execution + r'\)', log):
                load[name] = {'status': 'FAILED', 'reason': 'Gatling goal executed without JSON report'}
    oracle_scale = root / 'model-sync-persistence-jdbc/target/qm/oracle-scale.json'
    try:
        scale = None
        if full and oracle_scale.is_file():
            fresh(oracle_scale, root)
            scale = json.loads(oracle_scale.read_text(encoding='utf-8'))
            if not isinstance(scale, dict):
                raise ValueError('Malformed Oracle scale evidence')
    except (OSError, json.JSONDecodeError, ValueError) as error:
        scale = {'status': 'REJECTED', 'reason': str(error)}
    if scale is None and full and re.search(r'\bRunning\s+(?:[\w.]+\.)?OracleScaleIT\b', log):
        scale = {'status': 'FAILED', 'reason': 'OracleScaleIT executed without JSON report'}
    oracle_workload = root / 'model-sync-persistence-jdbc/target/qm/oracle-workload.json'
    measured = None
    if full and oracle_workload.is_file():
        try:
            fresh(oracle_workload, root)
            measured = json.loads(oracle_workload.read_text(encoding='utf-8'))
            if not isinstance(measured, dict) or measured.get('scope') != 'oracle-backed-reconciliation':
                raise ValueError('Malformed or mismatched Oracle workload evidence')
        except (OSError, json.JSONDecodeError, ValueError) as error:
            measured = {'status': 'REJECTED', 'reason': str(error)}
    if measured is None and full and re.search(r'\bRunning\s+(?:[\w.]+\.)?OracleCapacityIT\b', log):
        measured = {'status': 'FAILED', 'reason': 'OracleCapacityIT executed without JSON report'}
    return {'modules': modules, 'mutation': mutation, 'gatling': load, 'oracle_scale': scale,
            'oracle_workload': measured, 'rejected': log_rejected + [reason for module in modules.values()
                for suite in module.values() for reason in suite.get('rejected', [])],
            'attribution': run['run_id']}


def manifest(full: bool, root: Path = ROOT) -> dict:
    run = current(root)
    paths = []
    for module in reactor_modules(root, full):
        paths.extend((module / 'target/surefire-reports').glob('TEST-*.xml'))
        if full: paths.extend((module / 'target/failsafe-reports').glob('TEST-*.xml'))
    if full:
        paths.extend((root / 'model-sync-testkit/target/pit-reports').glob('**/*'))
        paths.extend((root / 'model-sync-performance/target/gatling').glob('**/*'))
        paths.extend((root / 'model-sync-performance/target/qm').glob('*.json'))
        paths.extend((root / 'model-sync-persistence-jdbc/target/qm').glob('*.json'))
    paths.extend((root / 'target/qm').glob('*.log'))
    paths.extend((root / 'target/qm').glob('oracle-container.json'))
    files = {}
    rejected = []
    for path in paths:
        if path.is_file():
            try:
                fresh(path, root)
                files[path.relative_to(root).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
            except (OSError, ValueError) as error:
                rejected.append(str(error))
    return {**run, 'files_sha256': files, 'rejected': rejected}


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
        f"Source at report generation: `{data['source_sha']}`; tree: `{data['source_tree']}`.",
        f"Evidence attribution: {evidence['attribution']}.",
        f"Working tree dirty at report generation: {str(data['working_tree_dirty']).lower()}.",
        f"Environment: {data['environment']['java']}; {data['environment']['maven']}; {data['environment']['python']}.",
        '', '## Commands and outcome', '',
        '| Lane | Exact command | Exit | Status |', '|---|---|---:|---|',
    ]
    for lane in ('default', 'full'):
        item = runs[lane]
        lines.append(f"| {lane} | `{item['command']}` | {item['exit'] if item['exit'] is not None else 'NOT_RUN'} | {item['status']} |")
    lines += ['', '## Measured evidence', '',
              f"Surefire: {unit['tests']} tests, {unit['failures']} failures, {unit['errors']} errors, {unit['skipped']} skipped across {sum(v['surefire']['files'] for v in evidence['modules'].values())} XML files.",
              f"Failsafe: {integration['tests']} tests, {integration['failures']} failures, {integration['errors']} errors, {integration['skipped']} skipped across {sum(v['failsafe']['files'] for v in evidence['modules'].values())} XML files."]
    pit = evidence['mutation']
    lines.append('PIT: ' + (f"{pit['status']}; {pit['generated']} mutations; {pit['counts']}; killed/generated {pit['killed_over_generated_percent']}%." if 'generated' in pit else pit['status'] + '.'))
    lines.append('Gatling: ' + (', '.join(f"{name}={item['status']}" for name, item in sorted(evidence['gatling'].items())) + ' in-memory semantic scenarios.' if evidence['gatling'] else 'NOT_RUN; no latency percentiles or completed-throughput measurement.'))
    lines.append('Oracle scale: ' + (json.dumps(evidence['oracle_scale'], sort_keys=True) if evidence['oracle_scale'] else 'NOT_RUN.'))
    lines.append('Oracle reconciliation workload: ' + (json.dumps(evidence['oracle_workload'], sort_keys=True) if evidence['oracle_workload'] else 'NOT_RUN.'))
    for module_name, module in evidence['modules'].items():
        for runner in ('surefire', 'failsafe'):
            for suite in module[runner].get('suites', []):
                if suite['failures'] or suite['errors'] or suite['skipped'] or suite.get('status') == 'FAILED':
                    lines.append(f"{module_name}/{runner}/{suite['name']}: FAILED; {suite['tests']} tests, "
                                 f"{suite['failures']} failures, {suite['errors']} errors, {suite['skipped']} skipped."
                                 + (f" {suite['reason']}." if suite.get('reason') else ''))
    lines += ['', '## Scope and blockers', '',
              'A default PASS does not qualify full release behavior. Full requires nonzero Oracle migration/concurrency/scale/capacity suites, selected PIT scope at 85% killed/generated, and all six Gatling reports. See `scripts/check-full-reports.py`.',
              'The local Gatling harness uses an in-memory test store; its data is not Oracle capacity evidence.',
              'No package publication or production deployment is authorized by this report.', '']
    if evidence['rejected']:
        lines.append('Rejected evidence: ' + '; '.join(evidence['rejected']))
    for kind, item in [('PIT', pit), *[(f'Gatling {name}', value) for name, value in evidence['gatling'].items()],
                       ('Oracle scale', evidence['oracle_scale']), ('Oracle workload', evidence['oracle_workload'])]:
        if isinstance(item, dict) and item.get('status') == 'REJECTED':
            lines.append(f"Rejected {kind} evidence: {item.get('reason', 'invalid report')}")
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
    full = runs['full']['status'] != 'NOT_RUN'
    evidence = collect(full)
    data = {'generated_utc': datetime.now(timezone.utc).isoformat(), 'source_sha': sha,
            'source_tree': tree, 'working_tree_dirty': dirty,
            'environment': {'java': command('java', '-version'),
                            'maven': command(str(ROOT / ('mvnw.cmd' if os.name == 'nt' else 'mvnw')), '-version'),
                            'python': command('python', '--version')},
            'runs': runs, 'evidence': evidence, 'blockers': args.blocker}
    out = ROOT / 'target/qm'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'evidence.json').write_text(json.dumps(data, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    if evidence['attribution'] != 'NONE':
        (out / 'raw-manifest.json').write_text(json.dumps(manifest(full), indent=2, sort_keys=True) + '\n', encoding='utf-8')
    else:
        (out / 'raw-manifest.json').unlink(missing_ok=True)
    report = markdown(data)
    (out / 'QM.md').write_text(report, encoding='utf-8')
    print(report)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as summary:
            summary.write('\n'.join(report.splitlines()[:14]) + '\n')


if __name__ == '__main__':
    main()
