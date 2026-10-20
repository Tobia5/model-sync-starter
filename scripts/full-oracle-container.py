#!/usr/bin/env python3
"""Explicit, disposable Oracle container lane for one full Maven verification run.

No existing container, user schema or host database is modified. Passwords are
generated for this invocation, kept out of command arguments and reports, and
the uniquely named container is stopped in all exit paths.
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
from run_evidence import current, identity, invalidate

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


def maven_command() -> list[str]:
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
    return [*command, '-Pfull', 'clean', 'verify']


def preflight() -> None:
    if os.environ.get('MODELSYNC_ORACLE_CONTAINER') != 'EXPLICIT_DISPOSABLE':
        raise ValueError('MODELSYNC_ORACLE_CONTAINER=EXPLICIT_DISPOSABLE is required')
    if os.environ.get('MODELSYNC_FULL_ENV_ACK') != 'DISPOSABLE_NONPRODUCTION':
        raise ValueError('Disposable nonproduction environment acknowledgment is required')
    if any(os.environ.get(k) for k in ('MODELSYNC_ORACLE_URL', 'MODELSYNC_ORACLE_USER',
                                      'MODELSYNC_ORACLE_PASSWORD')):
        raise ValueError('Container mode cannot reuse external Oracle credentials')
    maven_command()
    docker('info', '--format', '{{.Server.Version}}')


def main() -> int:
    invalidate(ROOT)
    attempt = {'attempt_id': str(uuid.uuid4()), **identity(ROOT), 'image': IMAGE,
               'runtime_schema': 'MS_TEST_RUNTIME', 'migration_schema': 'MS_TEST_MIGRATION'}
    selected = os.environ.get('MODELSYNC_MAVEN_SETTINGS')
    attempt['maven_settings'] = selected or 'USER_DEFAULT'
    if selected == PROJECT_SETTINGS and (ROOT / selected).is_file():
        attempt['maven_settings_sha256'] = hashlib.sha256((ROOT / selected).read_bytes()).hexdigest()
    try:
        preflight()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        detail = error_detail(error)
        print(f'BLOCKED full preflight: disposable Docker lane unavailable: {detail}', file=sys.stderr)
        if os.environ.get('MODELSYNC_ORACLE_CONTAINER') == 'EXPLICIT_DISPOSABLE':
            write_infrastructure({**attempt, 'status': 'BLOCKED', 'phase': 'Docker preflight',
                                  'error': detail, 'container_created': False,
                                  'cleanup_verified': True, 'run_id': None})
        return 2
    name = 'msq-' + uuid.uuid4().hex[:12]
    admin = secrets.token_hex(24)
    runtime = secrets.token_hex(24)
    migration = secrets.token_hex(24)
    schema = (ROOT / 'model-sync-persistence-jdbc/src/main/resources/model-sync/schema/oracle-v2.sql').read_text(encoding='utf-8')
    started = time.monotonic()
    result_code = 2
    cleanup_verified = False
    container_created = False
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
            f'connect MS_TEST_RUNTIME/{runtime}@//localhost:1521/FREEPDB1\n'
            + schema + '\ncommit;\nexit\n', encoding='utf-8')
        environment = dict(os.environ, ORACLE_PASSWORD=admin)
        phase = 'Oracle image pull/container start'
        blocker_detail = None
        try:
            run = docker('run', '--rm', '-d', '--name', name, '--platform', 'linux/amd64',
                         '--cpus', '4', '--memory', '6g', '-p', '127.0.0.1::1521',
                         '--health-cmd', 'healthcheck.sh', '--health-interval', '10s',
                         '--health-timeout', '5s', '--health-retries', '60',
                         '-v', f'{setup.resolve()}:/container-entrypoint-initdb.d/001-test-schemas.sql:ro',
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
            result = subprocess.run(maven_command(),
                                    cwd=ROOT, env=build_env, check=False)
            result_code = result.returncode
        except (OSError, subprocess.CalledProcessError, RuntimeError) as error:
            detail = error_detail(error)
            print(f'BLOCKED full preflight: {phase}: {detail}', file=sys.stderr)
            blocker_detail = detail
            result_code = 2
        finally:
            if container_created:
                docker('stop', name, check=False)
                inspected = docker('inspect', name, check=False)
                cleanup_verified = inspected.returncode != 0 and (
                    'No such object' in inspected.stderr or 'No such container' in inspected.stderr)
                if not cleanup_verified:
                    print('BLOCKED cleanup: disposable Oracle container was not removed', file=sys.stderr)
                    if result_code == 0:
                        result_code = 2
            try:
                run_id = current(ROOT)['run_id']
            except ValueError:
                run_id = None
            write_infrastructure({**attempt, 'run_id': run_id, 'container_name': name,
                                  'elapsed_seconds': round(time.monotonic() - started, 2),
                                  'container_created': container_created,
                                  'cleanup_verified': cleanup_verified if container_created else True,
                                  'phase': phase, 'error': blocker_detail,
                                  'status': 'PASS' if result_code == 0 else 'FAILED' if run_id else 'BLOCKED'})
    return result_code


if __name__ == '__main__':
    raise SystemExit(main())
