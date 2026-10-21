"""Admit current-run Oracle plan/counter diagnostics without treating them as throughput SLOs."""
from pathlib import Path
import json
import math
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


def selected_plans(value):
    """Check the selected production paths, without treating client fetch limits as a work bound."""
    plans(value)
    for cursor in value:
        text = cursor.get('SQL_TEXT', '')
        sources = cursor.get('row_sources')
        if (not isinstance(text, str) or not text.startswith('select /*+ index_asc(w MS_WORK_')
                or not isinstance(sources, list) or not sources or not all(isinstance(row, dict) for row in sources)):
            raise ValueError('Selected Oracle selector SQL/row sources absent')
        if any(str(row.get('OPERATION', '')).upper() == 'SORT' for row in sources):
            raise ValueError('Selected Oracle selector still sorts the eligible population')
        if not any(row.get('OPERATION') == 'INDEX' and row.get('OPTIONS') == 'RANGE SCAN'
                   and row.get('OBJECT_NAME') in {'MS_WORK_READY_IX', 'MS_WORK_RECOVER_IX', 'MS_WORK_BIND_READY_IX'}
                   for row in sources):
            raise ValueError('Selected Oracle ordered index range scan absent')


def check(root: Path, scale: dict, workload: dict):
    distributions = scale.get('distributions')
    if not isinstance(distributions, list) or {v.get('distribution') for v in distributions} != DISTRIBUTIONS:
        raise ValueError('Required due/future/expired Oracle distributions absent')
    for distribution in distributions:
        selected_plans(distribution.get('claim_plans'))
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
    selected_plans(phases.get('claim_plans'))
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
    if (any(type(workload.get(name)) is not int or workload[name] < 0
            for name in ('polls', 'empty_polls', 'selection_attempts', 'empty_selections'))
            or workload['polls'] != attempts + workload['empty_polls']
            or workload['selection_attempts'] != attempts + workload['empty_selections']):
        raise ValueError('Oracle empty-poll/selection accounting mismatch')


def optimization(root: Path):
    path = root / 'model-sync-persistence-jdbc/target/qm/oracle-optimization.json'
    fresh(path, root)
    value = json.loads(path.read_text(encoding='utf-8'))
    if (not isinstance(value, dict) or value.get('scope') != 'controlled-oracle-queue-comparison-not-production-capacity'
            or value.get('status') != 'PASS' or value.get('rows') != 100_000 or value.get('due_rows') != 100_000
            or value.get('workers') != 4 or value.get('pool') != 8 or value.get('owner_spacing_ms') != 20
            or value.get('owner_call_target') != 230 or value.get('cleanup_rows_remaining') != 0):
        raise ValueError('Oracle controlled comparison incomplete/failed or workload changed')
    variants = {'baseline', 'selector-only', 'admission-only', 'combined'}
    empty = value.get('empty_queue_results')
    if (not isinstance(empty, list) or len(empty) != 4 or not all(isinstance(v, dict) for v in empty)
            or {v.get('variant') for v in empty} != variants):
        raise ValueError('Oracle empty-queue comparison absent')
    for result in empty:
        if (result.get('polls') != 20 or result.get('empty_polls') != 20
                or any(result.get(k) != 0 for k in ('quota_grants', 'quota_denials', 'owner_calls'))):
            raise ValueError('Empty-queue polls consumed quota or called owner')
        counters(result.get('work', {}).get('session_statistics'))
    results = value.get('results')
    if (not isinstance(results, list) or len(results) != 8 or not all(isinstance(r, dict) for r in results)
            or {(r.get('round'), r.get('variant')) for r in results} != {(n, v) for n in (0, 1) for v in variants}):
        raise ValueError('Oracle four-way forward/reverse comparison absent')
    for result in results:
        integer_fields = ('polls', 'empty_polls', 'selection_attempts', 'empty_selections', 'selector_queries',
                          'ledger_attempts', 'quota_grants', 'quota_denials', 'measured_owner_calls',
                          'measured_completed_syncs', 'total_owner_calls', 'total_completed_syncs')
        if any(type(result.get(k)) is not int or result[k] < 0 for k in integer_fields):
            raise ValueError('Oracle comparison counters absent or malformed')
        if (result.get('status') != 'PASS' or result.get('total_owner_calls', 0) < 230
                or result.get('total_completed_syncs', 0) < 150 or result.get('measured_completed_syncs', 0) <= 0
                or result.get('quota_grants') != result.get('measured_owner_calls')
                or result.get('ledger_attempts') != result.get('quota_grants', 0) + result.get('quota_denials', 0)
                or result.get('polls') != result.get('ledger_attempts', 0) + result.get('empty_polls', 0)
                or result.get('selection_attempts') != result.get('ledger_attempts', 0) + result.get('empty_selections', 0)):
            raise ValueError('Oracle comparison outcome/accounting mismatch')
        counters(result.get('measurement_work', {}).get('session_statistics'))
        native = result['measurement_work']['session_statistics']
        logical = result.get('logical_gets_per_completed')
        if (type(logical) not in (int, float) or not math.isfinite(logical)
                or not math.isclose(logical, (native['consistent gets'] + native['db block gets'])
                                    / result['measured_completed_syncs'], rel_tol=1e-9)
                or result['selector_queries'] < result['ledger_attempts']):
            raise ValueError('Oracle comparison logical-work/selector metrics malformed')
        plans(result.get('claim_plans'))
        if result['variant'] in {'selector-only', 'combined'}:
            selected_plans(result['claim_plans'])
    for round_number in (0, 1):
        run = {r['variant']: r for r in results if r['round'] == round_number}
        if run['combined']['logical_gets_per_completed'] >= run['baseline']['logical_gets_per_completed']:
            raise ValueError('Combined did not reduce native work per completed reconciliation')
        if run['combined']['quota_denials'] > run['baseline']['quota_denials']:
            raise ValueError('Combined increased baseline denial churn')
        selector_denials = run['selector-only']['quota_denials']
        if (run['combined']['quota_denials'] >= selector_denials if selector_denials else run['combined']['quota_denials'] != 0):
            raise ValueError('Admission did not reduce selector-only churn or preserve zero')
    return value
