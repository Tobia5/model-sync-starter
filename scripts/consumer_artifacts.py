#!/usr/bin/env python3
"""Local-only staged artifacts and independent consumer verification. Never deploys."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import uuid
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
VERSION = '0.2.0-SNAPSHOT'
LIBRARIES = ('core', 'spring', 'persistence-jpa', 'persistence-jdbc', 'rest', 'reactor',
             'webclient', 'autoconfigure', 'spring-boot-starter', 'spring-boot-starter-oracle-jpa',
             'spring-boot-starter-webclient')
MODES = ('mvc', 'webflux')

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n', encoding='utf-8')

def outside(root, workspace):
    workspace = workspace.resolve()
    if workspace.is_relative_to(root.resolve()) or not workspace.is_dir():
        raise ValueError('Consumer workspace must already exist outside the producer checkout')
    return workspace

def artifact_files(repository, modules=LIBRARIES):
    result = []
    parent = repository / 'io/modelsync/model-sync-parent' / VERSION / f'model-sync-parent-{VERSION}.pom'
    if not parent.is_file(): raise ValueError('Staged parent POM absent')
    result.append(parent)
    for module in modules:
        artifact = 'model-sync-' + module
        directory = repository / 'io/modelsync' / artifact / VERSION
        for suffix in ('.pom', '.jar'):
            path = directory / f'{artifact}-{VERSION}{suffix}'
            if not path.is_file(): raise ValueError(f'Required artifact absent: {artifact}{suffix}')
            result.append(path)
        # The three source-less dependency-only starters have no source/Javadoc attachment.
        if module not in ('spring-boot-starter', 'spring-boot-starter-oracle-jpa', 'spring-boot-starter-webclient'):
            for suffix in ('-sources.jar', '-javadoc.jar'):
                path = directory / f'{artifact}-{VERSION}{suffix}'
                if not path.is_file(): raise ValueError(f'Required attachment absent: {artifact}{suffix}')
                result.append(path)
    return result

def verify_hashes(workspace, manifest):
    for item in manifest['artifacts']:
        relative = Path(item['path'])
        if relative.is_absolute() or '..' in relative.parts: raise ValueError('Invalid artifact path')
        path = workspace / 'staged' / relative
        if not path.is_file() or digest(path) != item['sha256']: raise ValueError('Staged artifact hash mismatch')
    if len(manifest['artifacts']) != 39: raise ValueError('Incomplete parent/library/attachment set')

def verify_consumer_artifacts(manifest):
    for item in manifest['consumers'].values():
        repository = Path(item['repository'])
        for artifact in manifest['artifacts']:
            path = repository / artifact['path']
            if not path.is_file() or digest(path) != artifact['sha256']:
                raise ValueError('Consumer cache artifact differs from the staged set')

def source_manifest(project):
    return {p.relative_to(project).as_posix(): digest(p) for p in sorted(project.rglob('*'))
            if p.is_file() and 'target' not in p.relative_to(project).parts
            and p.suffix in ('.java', '.xml', '.properties')}

def validate_tree(path):
    tree = json.loads(path.read_text(encoding='utf-8'))
    found = set()
    def visit(node):
        if node.get('groupId') == 'io.modelsync':
            artifact = node['artifactId']; found.add(artifact)
            if artifact in ('model-sync-sample', 'model-sync-testkit', 'model-sync-performance', 'model-sync-architecture-tests'):
                raise ValueError('Test/demo artifact leaked into consumer dependency tree')
            if node.get('version') != VERSION: raise ValueError('Unexpected Model Sync artifact version')
        for child in node.get('children', []): visit(child)
    visit(tree)
    required = {'model-sync-' + value for value in ('core', 'spring', 'reactor', 'autoconfigure', 'persistence-jpa', 'persistence-jdbc', 'rest', 'spring-boot-starter', 'spring-boot-starter-oracle-jpa')}
    if not required.issubset(found): raise ValueError('Consumer dependency tree omits required library modules')
    return sorted(found)

def maven(root, repository):
    return [str(root / ('mvnw.cmd' if os.name == 'nt' else 'mvnw')), '-B', '-ntp',
            '-s', str(root / '.mvn/qualification-central.settings.xml'), '-Dmaven.repo.local=' + str(repository)]

def stage(root, workspace, repository):
    from run_evidence import current
    run = current(root)
    workspace = outside(root, workspace)
    if (workspace / 'staged').exists(): raise ValueError('Staged artifact set is immutable; choose a new workspace')
    artifacts = artifact_files(repository)
    for path in artifacts:
        artifact = path.parent.parent.name
        module = root if artifact == 'model-sync-parent' else root / artifact
        built = module / 'pom.xml' if path.suffix == '.pom' else module / 'target' / path.name
        if not built.is_file() or digest(built) != digest(path):
            raise ValueError('Installed artifact does not match the source/build output: ' + path.name)
        if path.suffix == '.jar' and built.stat().st_mtime_ns < run['started_ns']:
            raise ValueError('Artifact predates current packaging run')
    target = workspace / 'staged'
    entries = []
    for path in artifacts:
        relative = path.relative_to(repository)
        destination = target / relative; destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, destination)
        entries.append({'path': relative.as_posix(), 'sha256': digest(path), 'bytes': path.stat().st_size})
    manifest = {'producer': run, 'artifacts': entries,
                'settings_sha256': digest(root / '.mvn/qualification-central.settings.xml'), 'consumers': {}}
    verify_hashes(workspace, manifest)
    templates = root / 'validation/consumers'
    for mode in MODES:
        project = workspace / mode
        if project.exists(): raise ValueError('Consumer project already exists')
        shutil.copytree(templates / 'common', project)
        shutil.copy2(templates / mode / 'pom.xml', project / 'pom.xml')
        shutil.copy2(templates / mode / 'ConsumerController.java', project / 'src/main/java/example/consumer/ConsumerController.java')
        cache = workspace / ('m2-' + mode); cache.mkdir(exist_ok=False)
        shutil.copytree(target / 'io', cache / 'io')
        manifest['consumers'][mode] = {'sources': source_manifest(project), 'repository': str(cache),
                                      'initial_repository': 'empty except exact staged io.modelsync artifacts',
                                      'prepare_exit': None, 'prepare_command': None}
    write(workspace / 'artifact-manifest.json', manifest)
    return manifest

def prepare(root, workspace):
    manifest = json.loads((workspace / 'artifact-manifest.json').read_text(encoding='utf-8'))
    verify_hashes(workspace, manifest)
    verify_consumer_artifacts(manifest)
    for mode in MODES:
        project = workspace / mode; item = manifest['consumers'][mode]
        command = [*maven(root, Path(item['repository'])), 'clean', 'test-compile',
            'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree', '-DoutputType=json',
            '-DoutputFile=target/dependency-tree.json',
            'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath', '-Dmdep.outputFile=target/classpath.txt',
            'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:go-offline']
        started = time.time_ns()
        with (workspace / (mode + '-prepare.log')).open('w', encoding='utf-8') as log:
            code = subprocess.run(command, cwd=project, stdout=log, stderr=subprocess.STDOUT).returncode
        item.update(prepare_exit=code, prepare_command=command, prepare_started_ns=started, prepare_ended_ns=time.time_ns())
        write(workspace / 'artifact-manifest.json', manifest)
        if code: return code
        item['resolved_modelsync'] = validate_tree(project / 'target/dependency-tree.json')
        classpath = (project / 'target/classpath.txt').read_text(encoding='utf-8')
        if str(root) in classpath or 'target/classes' in classpath or 'target\\classes' in classpath:
            raise ValueError('Producer reactor/workspace leaked into independent consumer classpath')
        for path in classpath.strip().split(os.pathsep):
            if not Path(path).resolve().is_relative_to(Path(item['repository']).resolve()):
                raise ValueError('Consumer classpath resolved outside its dedicated repository')
        item['classpath_sha256'] = hashlib.sha256(classpath.encode()).hexdigest()
    write(workspace / 'artifact-manifest.json', manifest)
    return 0

def launch_command(root, workspace):
    from run_evidence import current, identity
    import runpy
    workspace = outside(root, workspace)
    current(root)  # matching cheap default evidence; never borrowed from a different source
    runpy.run_path(str(root / 'scripts/check-test-reports.py'))['check'](root)
    if os.environ.get('MODELSYNC_MAVEN_SETTINGS') != '.mvn/qualification-central.settings.xml':
        raise ValueError('Consumer execution requires the approved project Maven settings')
    manifest = json.loads((workspace / 'artifact-manifest.json').read_text(encoding='utf-8'))
    for key, value in identity(root).items():
        if manifest['producer'].get(key) != value: raise ValueError('Staged source differs from frozen checkout')
    verify_hashes(workspace, manifest)
    verify_consumer_artifacts(manifest)
    for mode in MODES:
        item = manifest['consumers'][mode]
        if item.get('prepare_exit') != 0: raise ValueError('Independent consumer preparation did not pass')
        if item['sources'] != source_manifest(workspace / mode): raise ValueError('Consumer sources changed after staging')
        validate_tree(workspace / mode / 'target/dependency-tree.json')
    return [sys.executable, str(root / 'scripts/consumer_artifacts.py'), 'run', str(workspace)]

def admitted(observation):
    return (len(observation['samples']) == 3
            and min(s['host_available_gib'] for s in observation['samples']) >= 12
            and observation['docker']['available_gib'] >= 6
            and observation['docker']['cpu'] >= 4
            and min(observation['disk_free_gib']) >= 20)

def resource_admission(root, workspace):
    """One bounded observation, using the established small-diagnostic 12 GiB budget."""
    from run_evidence import identity
    record = {**identity(root), 'attempt_id': str(uuid.uuid4()), 'samples': [],
              'required_host_gib': 12, 'oracle_gib': 6, 'tools_gib': 4, 'reserve_gib': 2,
              'unchanged_full_budget_gib': 18, 'oracle_cpu_cap': 4}
    if os.name != 'nt': raise ValueError('This approved admission implementation requires the Windows host')
    for index in range(3):
        memory = subprocess.check_output(['powershell.exe', '-NoProfile', '-Command',
            '$m=Get-CimInstance Win32_OperatingSystem; [math]::Round($m.FreePhysicalMemory/1MB,3)'], text=True)
        record['samples'].append({'observed_ns': time.time_ns(), 'host_available_gib': float(memory.strip())})
        if index < 2: time.sleep(3)
    docker = json.loads(subprocess.check_output(['docker', 'info', '--format',
        '{"server":"{{.ServerVersion}}","cpu":{{.NCPU}},"memory_bytes":{{.MemTotal}},"running":{{.ContainersRunning}}}'], text=True))
    stats = subprocess.check_output(['docker', 'stats', '--no-stream', '--format', '{{.MemUsage}}'], text=True, timeout=20)
    units = {'B': 1, 'kB': 1000, 'KB': 1000, 'KiB': 1024, 'MB': 1000**2, 'MiB': 1024**2, 'GB': 1000**3, 'GiB': 1024**3}
    used = 0
    for line in stats.splitlines():
        match = re.fullmatch(r'([0-9.]+)\s*([A-Za-z]+)', line.partition('/')[0].strip())
        if not match or match[2] not in units: raise ValueError('Malformed Docker capacity observation')
        used += float(match[1]) * units[match[2]]
    docker['observed_usage_gib'] = used / 1024**3
    docker['available_gib'] = (docker['memory_bytes'] - used) / 1024**3
    record['docker'] = docker
    record['disk_free_gib'] = json.loads(subprocess.check_output(['powershell.exe', '-NoProfile', '-Command',
        '@(Get-PSDrive -Name C,D | ForEach-Object { [math]::Round($_.Free/1GB,3) }) | ConvertTo-Json -Compress'], text=True))
    record['status'] = 'PASS' if admitted(record) else 'BLOCKED'
    write(workspace / ('admission-' + record['attempt_id'] + '.json'), record)
    if record['status'] != 'PASS': raise ValueError('Consumer resource admission failed; see run-scoped admission receipt')
    return record

def check_receipt(path, run, manifest, mode, started, manifest_hash):
    if not path.is_file() or path.stat().st_mtime_ns < started: raise ValueError('Stale or missing consumer receipt')
    data = json.loads(path.read_text(encoding='utf-8'))
    expected = {'status': 'PASS', 'run_id': run, 'producer_source': manifest['producer']['source_sha'],
                'producer_tree': manifest['producer']['source_tree'], 'framework_mode': mode,
                'consumer_manifest_sha256': manifest_hash, 'datasource_enlisted': True,
                'target': 'oracle26-nowait-ledger-first-v1', 'committed_reconciliations': 3, 'owner_calls': 3,
                'disabled_mode': 'PASS', 'pool_closed': True, 'disabled_pool_closed': True,
                'fixture_rows_remaining': 0, 'owned_scheduler_threads_after_close': 0}
    for key, value in expected.items():
        if type(data.get(key)) is not type(value) or data[key] != value: raise ValueError('Consumer receipt mismatch: ' + key)
    if data.get('effective_policies') != {'point': 'ORACLE_NOWAIT', 'quota': 'ORACLE_NOWAIT',
            'insert': 'ORACLE_26_NOWAIT', 'jpa': 'ORACLE_NOWAIT', 'cooldown': 'DURABLE', 'extension': 1}:
        raise ValueError('Consumer receipt policy fallback')
    if data.get('shutdown', {}).get('status') != 'DRAINED' or data['shutdown'].get('physical') != 0 or data['shutdown'].get('owner') != 0:
        raise ValueError('Physical consumer drain unproven')
    return data

def run(root, workspace):
    manifest = json.loads((workspace / 'artifact-manifest.json').read_text(encoding='utf-8'))
    run_id = os.environ['CONSUMER_SESSION_ID']; result = {'run_id': run_id, 'modes': {}, 'full_status': 'NOT_RUN'}
    failure = 0
    for mode in MODES:
        item = manifest['consumers'][mode]; project = workspace / mode
        command = [*maven(root, Path(item['repository'])), '-o', 'verify']
        started = time.time_ns()
        env = dict(os.environ, CONSUMER_RUN_ID=run_id, CONSUMER_SOURCE_SHA=manifest['producer']['source_sha'],
                   CONSUMER_SOURCE_TREE=manifest['producer']['source_tree'],
                   CONSUMER_MANIFEST_SHA256=digest(workspace / 'artifact-manifest.json'),
                   CONSUMER_REPOSITORY=item['repository'])
        with (workspace / (mode + '-oracle.log')).open('w', encoding='utf-8') as log:
            code = subprocess.run(command, cwd=project, env=env, stdout=log, stderr=subprocess.STDOUT).returncode
        observed = {'command': command, 'cwd': str(project), 'original_exit': code, 'started_ns': started,
                    'ended_ns': time.time_ns(), 'status': 'FAILED' if code else 'PASS'}
        # Collect fresh partial results independently of qualification, including an executed failure.
        xml = project / 'target/surefire-reports/TEST-example.consumer.ConsumerJourneyTest.xml'
        observed['junit_status'] = 'NOT_RUN'
        if xml.is_file() and xml.stat().st_mtime_ns >= started:
            try:
                suite = ET.parse(xml).getroot()
                counts = {k: int(suite.get(k, '0')) for k in ('tests', 'failures', 'errors', 'skipped')}
                observed['junit_counts'] = counts
                observed['junit_status'] = 'PASS' if list(counts.values()) == [1, 0, 0, 0] else 'FAILED'
            except (ValueError, ET.ParseError): observed['junit_status'] = 'REJECTED'
        receipt_path = project / 'target/consumer-evidence/receipt.json'
        if receipt_path.is_file() and receipt_path.stat().st_mtime_ns >= started:
            try:
                partial = json.loads(receipt_path.read_text(encoding='utf-8'))
                if (partial.get('run_id') == run_id and partial.get('producer_source') == manifest['producer']['source_sha']
                        and partial.get('producer_tree') == manifest['producer']['source_tree']
                        and partial.get('consumer_manifest_sha256') == digest(workspace / 'artifact-manifest.json')):
                    observed['receipt'] = partial
            except ValueError: observed['receipt_collection'] = 'REJECTED'
        try:
            verify_consumer_artifacts(manifest)
            observed['receipt'] = check_receipt(project / 'target/consumer-evidence/receipt.json', run_id, manifest, mode, started,
                digest(workspace / 'artifact-manifest.json'))
            if observed['junit_status'] != 'PASS':
                raise ValueError('Required consumer JUnit test failed/missing/skipped')
        except (ValueError, OSError, ET.ParseError) as error:
            observed.update(status='FAILED', validation_error=str(error)); failure = failure or 1
        failure = failure or code
        result['modes'][mode] = observed; result['exit'] = failure
        write(workspace / ('consumer-run-' + run_id + '.json'), result)
    return failure

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('stage', 'prepare', 'run'))
    parser.add_argument('workspace', type=Path)
    parser.add_argument('--repository', type=Path)
    args = parser.parse_args()
    workspace = outside(ROOT, args.workspace)
    if args.action == 'stage': stage(ROOT, workspace, args.repository); code = 0
    elif args.action == 'prepare': code = prepare(ROOT, workspace)
    else: code = run(ROOT, workspace)
    raise SystemExit(code)
