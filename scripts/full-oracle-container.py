#!/usr/bin/env python3
"""Explicit, disposable Oracle container lane for one full Maven verification run.

No existing container, user schema or host database is modified. Passwords are
generated for this invocation, kept out of command arguments and reports, and
the uniquely named container and newly created labelled data volume are removed on exit.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import tempfile
import time
import uuid
import shutil
import argparse
import xml.etree.ElementTree as ET
from run_evidence import current, identity, invalidate, fresh

ROOT = Path(__file__).resolve().parents[1]
IMAGE = ('ghcr.io/gvenzl/oracle-free:23.26.3-slim@'
         'sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd')
PROJECT_SETTINGS = '.mvn/qualification-central.settings.xml'


def docker(*args: str, check: bool = True, env: dict | None = None) -> subprocess.CompletedProcess:
    return subprocess.run(['docker', *args], check=check, capture_output=True, text=True, env=env)


def error_detail(error: BaseException) -> str:
    if isinstance(error, subprocess.CalledProcessError) and error.stderr:
        return error.stderr.strip()
    return str(error)


def write_infrastructure(record: dict) -> None:
    destination = ROOT / 'target/qm/oracle-container.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(record, sort_keys=True) + '\n', encoding='utf-8')


def maven_command(diagnostic: bool = False) -> list[str]:
    wrapper = str(ROOT / ('mvnw.cmd' if os.name == 'nt' else 'mvnw'))
    command = [wrapper, '-B', '-ntp']
    selected = os.environ.get('MODELSYNC_MAVEN_SETTINGS')
    if selected:
        if selected != PROJECT_SETTINGS:
            raise ValueError('Only the reviewed project Maven settings may be selected')
        path = ROOT / selected
        if not path.is_file():
            raise ValueError('Reviewed project Maven settings file is absent')
        command.extend(('-s', str(path)))
    return ([*command, '-Pfull', '-N', 'initialize'] if diagnostic
            else [*command, '-Pfull', 'clean', 'verify'])


def diagnostic_command(root: Path = ROOT) -> list[str]:
    """Use only the current successful default build's compiled tests and resolved classpath."""
    current(root)
    for script in ('check-test-reports.py', 'check-default-log.py'):
        args = [sys.executable, str(root / 'scripts' / script)]
        if script == 'check-default-log.py':
            log = root / 'target/qm/default-build.log'
            fresh(log, root)
            raw = log.read_bytes()
            text = raw.decode('utf-16' if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8')
            if '[INFO] BUILD SUCCESS' not in text or '[INFO] BUILD FAILURE' in text:
                raise ValueError('A successful current-source default build log is required before diagnostics')
            args.append(str(log))
        result = subprocess.run(args, cwd=root, capture_output=True, text=True, check=False)
        if result.returncode:
            raise ValueError('A successful current-source default build is required before diagnostics')
    report = root / 'model-sync-sample/target/surefire-reports/TEST-io.modelsync.sample.JpaCompletionAtomicityTest.xml'
    fresh(report, root)
    properties = {p.get('name'): p.get('value') for p in ET.parse(report).getroot().findall('properties/property')}
    classpath = properties.get('java.class.path', '')
    if not classpath or 'junit-platform-launcher-' not in classpath:
        raise ValueError('Resolved JUnit launcher classpath absent from the current default report')
    compiled = root / 'model-sync-sample/target/test-classes/io/modelsync/sample/OracleDiagnosticRunner.class'
    fresh(compiled, root)
    jdbc_tests = root / 'model-sync-persistence-jdbc/target/test-classes'
    fresh(jdbc_tests / 'io/modelsync/jdbc/OracleScaleIT.class', root)
    java_home = os.environ.get('JAVA_HOME')
    java = str(Path(java_home) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')) if java_home else shutil.which('java')
    if not java or not Path(java).is_file():
        raise ValueError('The build JDK is unavailable for diagnostics')
    return [java, f'-Dmodelsync.repository.root={root}', '-cp',
            str(jdbc_tests) + os.pathsep + classpath, 'io.modelsync.sample.OracleDiagnosticRunner']


def apply_schema_script(container: str, script: str, passwords: tuple[str, ...]) -> None:
    result = subprocess.run(['docker', 'exec', '-i', container, 'sqlplus', '-s', '/', 'as', 'sysdba'],
                            input='set echo off\nset feedback off\n' + script,
                            capture_output=True, text=True, check=False)
    if result.returncode:
        detail = (result.stdout + result.stderr)[-4000:]
        for password in passwords:
            detail = detail.replace(password, '[REDACTED]')
        raise RuntimeError(f'Oracle isolated schema setup failed: {detail.strip()}')


def container_absent(result: subprocess.CompletedProcess) -> bool:
    return result.returncode != 0 and any(message in result.stderr.lower()
        for message in ('no such object', 'no such container'))


def volume_absent(result: subprocess.CompletedProcess) -> bool:
    return result.returncode != 0 and 'no such volume' in result.stderr.lower()


def create_session_volume(name: str, attempt_id: str) -> None:
    existing = docker('volume', 'inspect', name, check=False)
    if existing.returncode == 0:
        raise RuntimeError('Session volume already exists; refusing to reuse database storage')
    if not volume_absent(existing):
        raise RuntimeError('Cannot verify that session volume is absent: ' + existing.stderr.strip()[-1000:])
    docker('volume', 'create', '--label', f'io.modelsync.qualification.attempt={attempt_id}', name)
    try:
        labels = json.loads(docker('volume', 'inspect', '--format', '{{json .Labels}}', name).stdout)
    except json.JSONDecodeError as error:
        raise RuntimeError('Session volume ownership metadata was malformed') from error
    if not isinstance(labels, dict) or labels.get('io.modelsync.qualification.attempt') != attempt_id:
        raise RuntimeError('Session volume ownership did not match; refusing to mount it')


def remove_session_volume(name: str, attempt_id: str) -> bool:
    inspected = docker('volume', 'inspect', '--format', '{{json .Labels}}', name, check=False)
    if volume_absent(inspected):
        return True
    if inspected.returncode:
        return False
    try:
        labels = json.loads(inspected.stdout)
    except json.JSONDecodeError:
        return False
    if not isinstance(labels, dict) or labels.get('io.modelsync.qualification.attempt') != attempt_id:
        return False
    docker('volume', 'rm', name, check=False)
    return volume_absent(docker('volume', 'inspect', name, check=False))


def preflight() -> None:
    if os.environ.get('MODELSYNC_ORACLE_CONTAINER') != 'EXPLICIT_DISPOSABLE':
        raise ValueError('MODELSYNC_ORACLE_CONTAINER=EXPLICIT_DISPOSABLE is required')
    if os.environ.get('MODELSYNC_FULL_ENV_ACK') != 'DISPOSABLE_NONPRODUCTION':
        raise ValueError('Disposable nonproduction environment acknowledgment is required')
    if any(os.environ.get(k) for k in ('MODELSYNC_ORACLE_URL', 'MODELSYNC_ORACLE_USER',
                                      'MODELSYNC_ORACLE_PASSWORD')):
        raise ValueError('Container mode cannot reuse external Oracle credentials')
    maven_command()
    docker('info', '--format', '{{.ServerVersion}}')


def main(diagnostic: bool = False) -> int:
    attempt = {'attempt_id': str(uuid.uuid4()), **identity(ROOT), 'image': IMAGE,
               'runtime_schema': 'MS_TEST_RUNTIME', 'migration_schema': 'MS_TEST_MIGRATION',
               'mode': 'diagnostic' if diagnostic else 'full',
               'full_qualification': 'NOT_RUN' if diagnostic else 'REQUIRES_FULL_EVIDENCE'}
    selected = os.environ.get('MODELSYNC_MAVEN_SETTINGS')
    attempt['maven_settings'] = selected or 'USER_DEFAULT'
    if selected == PROJECT_SETTINGS and (ROOT / selected).is_file():
        attempt['maven_settings_sha256'] = hashlib.sha256((ROOT / selected).read_bytes()).hexdigest()
    try:
        launcher = diagnostic_command() if diagnostic else None
        invalidate(ROOT)
        preflight()
    except (ValueError, OSError, ET.ParseError, subprocess.CalledProcessError) as error:
        invalidate(ROOT)
        detail = error_detail(error)
        print(f'BLOCKED full preflight: disposable Docker lane unavailable: {detail}', file=sys.stderr)
        if os.environ.get('MODELSYNC_ORACLE_CONTAINER') == 'EXPLICIT_DISPOSABLE':
            write_infrastructure({**attempt, 'status': 'BLOCKED', 'phase': 'Docker preflight',
                                  'error': detail, 'container_created': False,
                                  'cleanup_verified': True, 'run_id': None,
                                  'maven_exit': None, 'wrapper_exit': 2})
        return 2
    name = 'msq-' + uuid.uuid4().hex[:12]
    volume_name = name + '-data'
    admin = secrets.token_hex(24)
    runtime = secrets.token_hex(24)
    migration = secrets.token_hex(24)
    schema = (ROOT / 'model-sync-persistence-jdbc/src/main/resources/model-sync/schema/oracle-v2.sql').read_text(encoding='utf-8')
    started = time.monotonic()
    result_code = 2
    maven_exit = None
    diagnostic_exit = None
    cleanup_verified = False
    container_created = False
    volume_created = False
    with tempfile.TemporaryDirectory(prefix='modelsync-oracle-') as directory:
        setup = Path(directory) / '001-test-schemas.sql'
        setup.write_text('whenever sqlerror exit failure rollback\n'
            'alter session set container=FREEPDB1;\n'
            f'create user MS_TEST_RUNTIME identified by "{runtime}";\n'
            f'create user MS_TEST_MIGRATION identified by "{migration}";\n'
            'grant create session, create table to MS_TEST_RUNTIME;\n'
            'grant create session, create table to MS_TEST_MIGRATION;\n'
            'alter user MS_TEST_RUNTIME quota unlimited on USERS;\n'
            'alter user MS_TEST_MIGRATION quota unlimited on USERS;\n'
            'grant select on v_$mystat to MS_TEST_RUNTIME;\n'
            'grant select on v_$statname to MS_TEST_RUNTIME;\n'
            'grant select on v_$sesstat to MS_TEST_RUNTIME;\n'
            'grant select on v_$session to MS_TEST_RUNTIME;\n'
            'grant select on v_$session_event to MS_TEST_RUNTIME;\n'
            'grant select on v_$sql to MS_TEST_RUNTIME;\n'
            'grant select on v_$sql_plan to MS_TEST_RUNTIME;\n'
            'grant select on v_$sql_plan_statistics_all to MS_TEST_RUNTIME;\n'
            f'connect MS_TEST_RUNTIME/{runtime}@//localhost:1521/FREEPDB1\n'
            + schema + '\ncommit;\nexit\n', encoding='utf-8')
        environment = dict(os.environ, ORACLE_PASSWORD=admin)
        phase = 'Oracle session data volume creation'
        blocker_detail = None
        try:
            create_session_volume(volume_name, attempt['attempt_id'])
            volume_created = True
            phase = 'Oracle image pull/container start'
            run = docker('run', '--rm', '-d', '--name', name, '--platform', 'linux/amd64',
                         '--label', f'io.modelsync.qualification.attempt={attempt["attempt_id"]}',
                         '--mount', f'type=volume,source={volume_name},target=/opt/oracle/oradata',
                         '--cpus', '4', '--memory', '6g', '-p', '127.0.0.1::1521',
                         '--health-cmd', 'healthcheck.sh', '--health-interval', '10s',
                         '--health-timeout', '5s', '--health-retries', '60',
                         '-e', 'ORACLE_PASSWORD', IMAGE, env=environment)
            container_created = bool(run.stdout.strip())
            phase = 'Oracle database startup/health'
            deadline = time.monotonic() + 900
            while time.monotonic() < deadline:
                status = docker('inspect', '--format', '{{.State.Health.Status}}', name).stdout.strip()
                if status == 'healthy': break
                if status == 'unhealthy': raise RuntimeError('Oracle container health check failed')
                time.sleep(10)
            else:
                raise RuntimeError('Oracle container startup exceeded 900 seconds')
            phase = 'Oracle isolated schema setup'
            apply_schema_script(name, setup.read_text(encoding='utf-8'), (admin, runtime, migration))
            port_output = docker('port', name, '1521/tcp').stdout.strip()
            match = re.search(r'127\.0\.0\.1:(\d+)', port_output)
            if not match: raise RuntimeError('Loopback Oracle port unavailable')
            url = f'jdbc:oracle:thin:@127.0.0.1:{match.group(1)}/FREEPDB1'
            build_env = dict(os.environ,
                MODELSYNC_ORACLE_URL=url, MODELSYNC_ORACLE_USER='MS_TEST_RUNTIME',
                MODELSYNC_ORACLE_PASSWORD=runtime,
                MODELSYNC_ORACLE_MIGRATION_URL=url,
                MODELSYNC_ORACLE_MIGRATION_USER='MS_TEST_MIGRATION',
                MODELSYNC_ORACLE_MIGRATION_PASSWORD=migration,
                MODELSYNC_ORACLE_TEST_SCHEMA_ACK='DEDICATED_TEST_SCHEMA',
                MODELSYNC_ORACLE_MIGRATION_SCHEMA_ACK='DEDICATED_MIGRATION_SCHEMA')
            phase = 'full Maven verification'
            result = subprocess.run(maven_command(diagnostic),
                                    cwd=ROOT, env=build_env, check=False)
            maven_exit = result.returncode
            result_code = maven_exit
            if diagnostic and maven_exit == 0:
                phase = 'selected Oracle diagnostics (not full qualification)'
                result = subprocess.run(launcher, cwd=ROOT / 'model-sync-persistence-jdbc',
                                        env=build_env, check=False)
                diagnostic_exit = result.returncode
                result_code = diagnostic_exit
        except (OSError, subprocess.CalledProcessError, RuntimeError) as error:
            detail = error_detail(error)
            print(f'BLOCKED full preflight: {phase}: {detail}', file=sys.stderr)
            blocker_detail = detail
            result_code = 2
        finally:
            observed = docker('inspect', '--format',
                              '{{.State.Running}}|{{index .Config.Labels "io.modelsync.qualification.attempt"}}',
                              name, check=False)
            if observed.returncode == 0:
                running, _, owner = observed.stdout.strip().partition('|')
                if owner == attempt['attempt_id']:
                    container_created = True
                    if running == 'true':
                        docker('stop', name, check=False)
                    docker('rm', name, check=False)
                    cleanup_verified = container_absent(docker('inspect', name, check=False))
            else:
                cleanup_verified = container_absent(observed)
            container_cleanup_verified = cleanup_verified
            volume_cleanup_verified = remove_session_volume(volume_name, attempt['attempt_id'])
            cleanup_verified = container_cleanup_verified and volume_cleanup_verified
            if not cleanup_verified:
                print('BLOCKED cleanup: session container or data volume removal could not be verified', file=sys.stderr)
                if result_code == 0:
                    result_code = 2
            try:
                run_id = current(ROOT)['run_id']
            except ValueError:
                run_id = None
            write_infrastructure({**attempt, 'run_id': run_id, 'container_name': name,
                                  'elapsed_seconds': round(time.monotonic() - started, 2),
                                  'container_created': container_created,
                                  'volume_name': volume_name, 'volume_created': volume_created,
                                  'container_cleanup_verified': container_cleanup_verified,
                                  'volume_cleanup_verified': volume_cleanup_verified,
                                  'cleanup_verified': cleanup_verified,
                                  'phase': phase, 'error': blocker_detail,
                                  'maven_exit': maven_exit, 'wrapper_exit': result_code,
                                  'diagnostic_exit': diagnostic_exit,
                                  'status': 'PASS' if result_code == 0 else 'FAILED' if run_id else 'BLOCKED'})
    return result_code


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--diagnostic', action='store_true',
                        help='Run the current default build\'s Oracle atomicity and 100k diagnostics; never qualifies full')
    raise SystemExit(main(parser.parse_args().diagnostic))
