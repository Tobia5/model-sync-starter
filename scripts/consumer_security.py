#!/usr/bin/env python3
"""Opt-in security journeys using the immutable locally staged 551427b artifacts."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import runpy
import shutil
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET
import consumer_artifacts as c
from run_evidence import identity, current

ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_SOURCE = '551427bed4d021339fcc32fef8f633232ff8deb8'
BASE_MANIFEST = '247c546b54c49b029d77de0420b09aead4b20f2d9bcc8e56cb48c9d032c3c166'
NEGATIVE_CASES = ({'unauthenticated-read', 'unauthenticated-submit', 'missing-csrf', 'unknown-operation'}
    | {prefix + op for prefix in ('cross-tenant-', 'unknown-tenant-', 'wrong-binding-', 'wrong-subject-', 'missing-authority-', 'direct-anonymous-') for op in ('read', 'refresh', 'event')}
    | {prefix + op for prefix in ('reader-cannot-', 'direct-reader-') for op in ('refresh', 'event')}
    | {prefix + op for prefix in ('refresher-cannot-', 'direct-cross-') for op in ('read', 'event')})

def unchanged_library(root):
    paths = ['pom.xml', '.mvn', 'mvnw', 'mvnw.cmd', 'model-sync-*/pom.xml', 'model-sync-*/src/main/**']
    if subprocess.check_output(['git', 'diff', ARTIFACT_SOURCE, '--', *paths], cwd=root):
        raise ValueError('Library/platform changed; old staged qualification cannot be carried forward')

def stage(root, workspace, artifacts):
    artifacts = c.outside(root, artifacts)
    if c.digest(artifacts / 'artifact-manifest.json') != BASE_MANIFEST:
        raise ValueError('Expected immutable consumer-tested artifact manifest')
    baseline = json.loads((artifacts / 'artifact-manifest.json').read_text())
    c.verify_hashes(artifacts, baseline); unchanged_library(root)
    manifest = {'kind': 'consumer-security-v1', 'fixture_source': identity(root), 'producer': baseline['producer'],
                'baseline_manifest_sha256': BASE_MANIFEST, 'artifacts': baseline['artifacts'],
                'settings_sha256': c.digest(root / '.mvn/qualification-central.settings.xml'), 'consumers': {}}
    shutil.copytree(artifacts / 'staged', workspace / 'staged')
    for mode in c.MODES:
        project = workspace / mode
        shutil.copytree(root / 'validation/consumers/common/src/main', project / 'src/main')
        for overlay in ('common', mode):
            shutil.copytree(root / 'validation/consumer-security' / overlay / 'src', project / 'src', dirs_exist_ok=True)
        shutil.copy2(root / 'validation/consumer-security' / mode / 'pom.xml', project / 'pom.xml')
        cache = workspace / ('m2-' + mode); cache.mkdir(exist_ok=False)
        shutil.copytree(workspace / 'staged/io', cache / 'io')
        manifest['consumers'][mode] = {'sources': c.source_manifest(project), 'repository': str(cache),
            'initial_repository': 'empty except exact immutable staged Model Sync artifacts', 'prepare_exit': None}
    c.write(workspace / 'artifact-manifest.json', manifest)

def validate_inputs(root, workspace):
    m = json.loads((workspace / 'artifact-manifest.json').read_text())
    if m.get('kind') != 'consumer-security-v1' or m.get('baseline_manifest_sha256') != BASE_MANIFEST or m['producer']['source_sha'] != ARTIFACT_SOURCE:
        raise ValueError('Incorrect artifact/security scope')
    if m['fixture_source'] != identity(root): raise ValueError('Security fixture source changed')
    unchanged_library(root); c.verify_hashes(workspace, m); c.verify_consumer_artifacts(m)
    for mode in c.MODES:
        if c.source_manifest(workspace / mode) != m['consumers'][mode]['sources']:
            raise ValueError('Security consumer sources changed after staging')
    return m

def prepare(root, workspace):
    m = validate_inputs(root, workspace); failure = 0
    for mode in c.MODES:
        item = m['consumers'][mode]
        command = [*c.maven(root, Path(item['repository'])), 'clean', 'test',
            'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree', '-DoutputType=json', '-DoutputFile=target/dependency-tree.json',
            'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath', '-Dmdep.outputFile=target/classpath.txt',
            'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:go-offline',
            'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get', '-Dartifact=org.apache.maven.surefire:surefire-junit-platform:3.5.3']
        item['prepare_command'] = command; item['prepare_started_ns'] = time.time_ns()
        with (workspace / (mode + '-prepare.log')).open('w', encoding='utf-8') as log:
            code = subprocess.run(command, cwd=workspace / mode, stdout=log, stderr=subprocess.STDOUT).returncode
        item['prepare_exit'] = code; item['prepare_ended_ns'] = time.time_ns()
        if not code:
            # Resolve the opt-in plugin without invoking integration-test/verify or Oracle.
            profile_command = [*c.maven(root, Path(item['repository'])), '-Psecurity-oracle',
                'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:go-offline']
            item['profile_resolution_command'] = profile_command
            with (workspace / (mode + '-profile-resolution.log')).open('w', encoding='utf-8') as log:
                code = subprocess.run(profile_command, cwd=workspace / mode, stdout=log, stderr=subprocess.STDOUT).returncode
            item['profile_resolution_exit'] = code; item['prepare_exit'] = code
        if not code:
            item['resolved_modelsync'] = c.validate_tree(workspace / mode / 'target/dependency-tree.json')
            paths = (workspace / mode / 'target/classpath.txt').read_text().strip().split(os.pathsep)
            if any(not Path(p).resolve().is_relative_to(Path(item['repository']).resolve()) for p in paths):
                raise ValueError('External source/classes leaked into classpath')
            item['classpath_sha256'] = c.digest(workspace / mode / 'target/classpath.txt')
        failure = failure or code
        c.write(workspace / 'artifact-manifest.json', m)
    return failure

def launch_command(root, workspace):
    current(root); runpy.run_path(str(root / 'scripts/check-test-reports.py'))['check'](root)
    if os.environ.get('MODELSYNC_MAVEN_SETTINGS') != '.mvn/qualification-central.settings.xml':
        raise ValueError('Approved project Maven settings required')
    m = validate_inputs(root, workspace)
    if subprocess.check_output(['git', 'status', '--porcelain'], cwd=root).strip():
        raise ValueError('Clean frozen security fixture source required')
    if any(v['prepare_exit'] != 0 for v in m['consumers'].values()):
        raise ValueError('Cheap security suites/preparation did not pass')
    return [sys.executable, str(root / 'scripts/consumer_security.py'), 'run', str(workspace)]

def check_receipt(data, run_id, manifest, mode, manifest_hash):
    if data.get('run_id') != run_id or data.get('fixture_source') != manifest['fixture_source']['source_sha'] or data.get('fixture_tree') != manifest['fixture_source']['source_tree']:
        raise ValueError('Stale or mismatched security evidence')
    if data.get('artifact_source') != ARTIFACT_SOURCE or data.get('manifest_sha256') != manifest_hash or data.get('mode') != mode:
        raise ValueError('Artifact/run scope mismatch')
    if data.get('status') != 'PASS' or data.get('owner_calls') != 4 or data.get('committed_reconciliations') != 4:
        raise ValueError('Missing/failing committed security journey')
    cases = data.get('negative_cases', [])
    if (len(cases) != len(NEGATIVE_CASES) or {case.get('case') for case in cases} != NEGATIVE_CASES
            or any(case.get('side_effects_unchanged') is not True for case in cases)):
        raise ValueError('Missing negative side-effect evidence')
    for key in ('pool_closed', 'disabled_mode', 'artifact_resolution', 'enlistment', 'rollback', 'trusted_worker_scope', 'cleanup'):
        if data.get(key) is not True: raise ValueError('Missing required security control: ' + key)
    if data.get('owned_threads_remaining') != 0 or data.get('active_owner_operations') != 0:
        raise ValueError('Owned operations did not drain')
    if mode == 'webflux' and data.get('concurrent_identity_checks') != 32:
        raise ValueError('Missing concurrent reactive identity proof')

def run(root, workspace):
    m = validate_inputs(root, workspace)
    run_id = os.environ['CONSUMER_SESSION_ID']; manifest_hash = c.digest(workspace / 'artifact-manifest.json')
    result = {'run_id': run_id, 'fixture_source': m['fixture_source'], 'artifact_source': m['producer'], 'modes': {}, 'full': 'NOT_RUN'}
    c.write(workspace / ('consumer-run-' + run_id + '.json'), result)
    failure = 0
    for mode in c.MODES:
        project = workspace / mode
        command = [*c.maven(root, Path(m['consumers'][mode]['repository'])), '-o', '-Psecurity-oracle', 'verify']
        env = dict(os.environ, SECURITY_RUN_ID=run_id, SECURITY_FIXTURE_SOURCE=m['fixture_source']['source_sha'],
            SECURITY_FIXTURE_TREE=m['fixture_source']['source_tree'], SECURITY_ARTIFACT_SOURCE=ARTIFACT_SOURCE,
            SECURITY_MANIFEST_SHA256=manifest_hash, CONSUMER_REPOSITORY=m['consumers'][mode]['repository'])
        observed = {'command': command, 'cwd': str(project), 'started_ns': time.time_ns(), 'status': 'NOT_RUN'}
        with (workspace / (mode + '-runtime.log')).open('w', encoding='utf-8') as log:
            code = subprocess.run(command, cwd=project, env=env, stdout=log, stderr=subprocess.STDOUT).returncode
        observed.update(original_exit=code, ended_ns=time.time_ns(), status='PASS' if code == 0 else 'FAILED')
        # Collection is independent of qualification; retain fresh failing receipts and original exits.
        receipt = project / 'target/consumer-evidence/security.json'
        try:
            suites = []
            for folder in ('surefire-reports', 'failsafe-reports'):
                for xml in (project / 'target' / folder).glob('TEST-*.xml'):
                    if xml.stat().st_mtime_ns < observed['started_ns']: raise ValueError('Stale JUnit XML')
                    suite = ET.parse(xml).getroot(); suites.append({'name': suite.get('name'), **{k: int(suite.get(k, 0)) for k in ('tests','failures','errors','skipped')}})
            observed['suites'] = suites
            if receipt.stat().st_mtime_ns < observed['started_ns']: raise ValueError('Stale receipt')
            data = json.loads(receipt.read_text())
            if data.get('run_id') != run_id: raise ValueError('Different run receipt')
            observed['receipt'] = data
            expected = {'example.consumer.AuthorizationBoundaryTest': 5 if mode == 'mvc' else 6, 'example.consumer.SecuredConsumerIT': 1}
            if {s['name']: s['tests'] for s in suites} != expected or any(s[k] for s in suites for k in ('failures','errors','skipped')):
                raise ValueError('Required security suites missing/failing/skipped')
            check_receipt(data, run_id, m, mode, manifest_hash)
        except (ValueError, OSError, ET.ParseError) as error:
            observed.update(status='FAILED', validation_error=type(error).__name__ + ': ' + str(error)); failure = failure or 1
        failure = failure or code; result['modes'][mode] = observed; result['exit'] = failure
        c.write(workspace / ('consumer-run-' + run_id + '.json'), result)
    return failure

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__);p.add_argument('action', choices=('stage','prepare','run'));p.add_argument('workspace', type=Path);p.add_argument('--artifacts', type=Path)
    args = p.parse_args(); workspace = c.outside(ROOT, args.workspace)
    if args.action == 'stage': stage(ROOT, workspace, args.artifacts); code = 0
    elif args.action == 'prepare': code = prepare(ROOT, workspace)
    else: code = run(ROOT, workspace)
    raise SystemExit(code)
