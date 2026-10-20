"""Admit current-run Oracle plan/counter diagnostics without treating them as throughput SLOs."""
from pathlib import Path
import json
from run_evidence import fresh

DISTRIBUTIONS = {'all-due', 'due-future', 'expired-future', 'mixed', 'all-future',
                 'all-due-with-stats', 'mixed-with-stats', 'all-future-with-stats'}
COUNTERS = {'db block gets', 'consistent gets', 'physical reads', 'execute count', 'redo size'}


def counters(value):
    if not isinstance(value, dict) or not COUNTERS <= value.keys():
        raise ValueError('Separate Oracle counters absent')
    if any(type(value[name]) is not int or value[name] < 0 for name in COUNTERS):
        raise ValueError('Oracle counters malformed or regressed')


def plans(value):
    if not isinstance(value, list) or not value:
        raise ValueError('Actual Oracle claim plans absent')
    for cursor in value:
        if not isinstance(cursor, dict) or not cursor.get('SQL_ID') or cursor.get('EXECUTIONS', 0) < 1:
            raise ValueError('Oracle cursor identity/execution absent')
        if (not cursor.get('CHILD_ADDRESS') or not cursor.get('LAST_LOAD_TIME')
                or cursor.get('LOADS', 0) < 1 or cursor.get('window_executions', 0) < 1):
            raise ValueError('Oracle loaded-cursor identity/window execution absent')
        plan = cursor.get('actual_plan')
        if not isinstance(plan, list) or not any('A-Rows' in str(line) for line in plan):
            raise ValueError('Oracle actual row-source statistics absent')
        if any('cannot fetch plan' in str(line) for line in plan):
            raise ValueError('Oracle actual plan unavailable')


def check(root: Path, scale: dict, workload: dict):
    distributions = scale.get('distributions')
    if not isinstance(distributions, list) or {v.get('distribution') for v in distributions} != DISTRIBUTIONS:
        raise ValueError('Required due/future/expired Oracle distributions absent')
    for distribution in distributions:
        plans(distribution.get('claim_plans'))
        samples = distribution.get('samples')
        if not isinstance(samples, list) or len(samples) != 3:
            raise ValueError('Oracle claim diagnostic samples absent')
        for sample in samples:
            counters(sample.get('claim_work', {}).get('session_statistics'))
            expected = 0 if distribution['distribution'].startswith('all-future') else 1
            if sample.get('ledger_claims') != expected:
                raise ValueError('Oracle distribution claim outcome mismatch')
    path = root / 'model-sync-persistence-jdbc/target/qm/oracle-workload-phases.json'
    fresh(path, root)
    phases = json.loads(path.read_text(encoding='utf-8'))
    for name in ('seed_work', 'warmup_work', 'measurement_work', 'cleanup_work'):
        phase = phases.get(name)
        if not isinstance(phase, dict) or not isinstance(phase.get('nonidle_session_waits'), dict):
            raise ValueError(f'Oracle phase waits absent: {name}')
        counters(phase.get('session_statistics'))
    plans(phases.get('claim_plans'))
    if phases.get('cleanup_rows_remaining') != 0 or scale.get('cleanup_rows_remaining') != 0:
        raise ValueError('Oracle fixture row cleanup unverified')
    grants = workload.get('measured_quota_grants')
    denials = workload.get('measured_quota_denials')
    attempts = workload.get('ledger_attempts')
    if (any(type(value) is not int or value < 0 for value in (grants, denials, attempts))
            or grants != workload.get('measured_owner_calls') or grants + denials != attempts
            or phases.get('measured_quota_grants') != grants
            or phases.get('measured_quota_denials') != denials
            or phases.get('measured_ledger_attempts') != attempts):
        raise ValueError('Oracle claim/quota/owner accounting mismatch')
