#!/usr/bin/env python3
"""Fail before expensive full goals when suites are filtered or disposable inputs are absent."""
import os
import re
import sys
from urllib.parse import urlsplit

args = dict(arg.split('=', 1) for arg in sys.argv[1:] if '=' in arg)
def supplied(value):
    return value is not None and not value.startswith('${') and value.strip() != ''

errors = []
for key in ('skipTests', 'maven.test.skip', 'skipITs', 'failsafe.skipITs',
            'pitest.skip', 'gatling.skip', 'pit.dryRun'):
    if supplied(args.get(key)) and args[key].lower() not in ('false', '0'):
        errors.append(f'{key} would skip required full verification')
for key in ('test', 'it.test', 'groups', 'excludedGroups', 'pit.targetClasses',
            'pit.targetTests', 'gatling.simulationClass'):
    if supplied(args.get(key)):
        errors.append(f'{key} would filter the declared full suites')

required = ('MODELSYNC_ORACLE_URL', 'MODELSYNC_ORACLE_USER', 'MODELSYNC_ORACLE_PASSWORD',
            'MODELSYNC_ORACLE_MIGRATION_URL', 'MODELSYNC_ORACLE_MIGRATION_USER',
            'MODELSYNC_ORACLE_MIGRATION_PASSWORD')
for key in required:
    if not os.environ.get(key): errors.append(f'{key} unavailable')
for key in ('MODELSYNC_ORACLE_USER', 'MODELSYNC_ORACLE_MIGRATION_USER'):
    value = os.environ.get(key, '')
    if value and not re.fullmatch(r'MS_TEST_[A-Za-z0-9_]{1,20}', value):
        errors.append(f'{key} is not a dedicated MS_TEST_ account')
if (os.environ.get('MODELSYNC_ORACLE_USER') and
    os.environ.get('MODELSYNC_ORACLE_USER') == os.environ.get('MODELSYNC_ORACLE_MIGRATION_USER')):
    errors.append('Runtime and migration test schemas must be separate')
if os.environ.get('MODELSYNC_FULL_ENV_ACK') != 'DISPOSABLE_NONPRODUCTION':
    errors.append('Disposable nonproduction environment acknowledgment unavailable')
if os.environ.get('MODELSYNC_ORACLE_TEST_SCHEMA_ACK') != 'DEDICATED_TEST_SCHEMA':
    errors.append('Dedicated disposable Oracle schema acknowledgment unavailable')
if os.environ.get('MODELSYNC_ORACLE_MIGRATION_SCHEMA_ACK') != 'DEDICATED_MIGRATION_SCHEMA':
    errors.append('Dedicated disposable migration schema acknowledgment unavailable')
target = os.environ.get('MODELSYNC_LOAD_TARGET', 'http://127.0.0.1:18088')
url = urlsplit(target)
if url.scheme != 'http' or not url.hostname:
    errors.append('Load target must use an explicit HTTP test endpoint')
elif url.hostname not in ('127.0.0.1', 'localhost'):
    hosts = set(filter(None, os.environ.get('MODELSYNC_APPROVED_NONPROD_HOSTS', '').split(',')))
    if (url.hostname not in hosts or
        os.environ.get('MODELSYNC_LOAD_TARGET_APPROVAL') != 'APPROVED_NONPRODUCTION'):
        errors.append('Remote load target lacks approved nonproduction host configuration')
if errors:
    print('BLOCKED full preflight: ' + '; '.join(errors), file=sys.stderr)
    raise SystemExit(2)
print('PASS full preflight: required suites enabled and disposable inputs present; secrets not displayed')
