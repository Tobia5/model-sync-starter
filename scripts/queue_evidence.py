"""Admit current-run Oracle plan/counter diagnostics without treating them as throughput SLOs."""
from pathlib import Path
import json
import math
from run_evidence import fresh, current
from comparison_accounting import validate as accounting, drain, probe

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
                or cursor.get('LOADS', 0) < 1):
            raise ValueError('Oracle loaded-cursor identity absent')
        attribution = cursor.get('attribution')
        if attribution == 'VALID':
            if type(cursor.get('window_executions')) is not int or cursor['window_executions'] < 1:
                raise ValueError('Oracle loaded-cursor continuous execution absent')
        elif attribution == 'UNATTRIBUTABLE':
            if (cursor.get('reason') not in {'MISSING_BEFORE', 'LOAD_IDENTITY_CHANGED', 'COUNTER_RESET'}
                    or cursor.get('window_executions') is not None or cursor.get('counter_deltas') is not None):
                raise ValueError('Ambiguous Oracle cursor must not supply window counters')
        else:
            raise ValueError('Oracle cursor attribution absent')
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
        expected = ('MS_WORK_BIND_READY_IX' if 'MS_WORK_BIND_READY_IX' in text else
                    'MS_WORK_RECOVER_IX' if 'MS_WORK_RECOVER_IX' in text else 'MS_WORK_READY_IX')
        if not any(row.get('OPERATION') == 'INDEX' and row.get('OPTIONS') == 'RANGE SCAN'
                   and row.get('OBJECT_NAME') == expected
                   for row in sources):
            raise ValueError('Selected Oracle ordered index range scan absent')


def cleanup_evidence(phases):
    proofs = []
    for name in ('cleanup_preflight', 'cleanup_verification'):
        proof = phases.get(name)
        if (not isinstance(proof, dict) or proof.get('strategy') not in
                {'SCOPED_DELETE', 'OWNED_CONTAINER_TRUNCATE'} or proof.get('scope_verified') is not True):
            raise ValueError('Oracle fixture cleanup scope/strategy unverified')
        if (proof['strategy'] == 'OWNED_CONTAINER_TRUNCATE'
                and (proof.get('foreign_key_restored') is not True or proof.get('orphan_probe_rejected') is not True)):
            raise ValueError('Oracle fixture foreign-key restoration/enforcement unverified')
        proofs.append(proof['strategy'])
    if len(set(proofs)) != 1:
        raise ValueError('Oracle fixture cleanup strategy changed after preflight')


def check(root: Path, scale: dict, workload: dict):
    distributions = scale.get('distributions')
    if not isinstance(distributions, list) or {v.get('distribution') for v in distributions} != DISTRIBUTIONS:
        raise ValueError('Required due/future/expired Oracle distributions absent')
    for distribution in distributions:
        selected_plans(distribution.get('claim_plans'))
        plans(distribution.get('legacy_claim_plans'))
        legacy = distribution.get('legacy_samples')
        if not isinstance(legacy, list) or len(legacy) != 3:
            raise ValueError('Same-population legacy selector comparison absent')
        for sample in legacy:
            counters(sample.get('claim_work', {}).get('session_statistics'))
            if sample.get('selector_queries') != 1:
                raise ValueError('Legacy selector execution accounting absent')
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
    cleanup_evidence(phases)
    grants = workload.get('measured_quota_grants')
    denials = workload.get('measured_quota_denials')
    attempts = workload.get('ledger_attempts')
    if (any(type(value) is not int or value < 0 for value in (grants, denials, attempts))
            or grants != workload.get('measured_owner_calls')
            or phases.get('measured_quota_grants') != grants
            or phases.get('measured_quota_denials') != denials
            or phases.get('measured_ledger_attempts') != attempts):
        raise ValueError('Oracle claim/quota/owner accounting mismatch')
    accounting(workload.get('accounting'), workload.get('measured_owner_calls'), workload.get('measured_completed_syncs'))
    accounting(workload.get('warmup_accounting'), workload.get('warmup_owner_calls'), workload.get('warmup_completed_syncs'))
    drain(workload.get('physical_drain'))
    if (attempts != workload['accounting']['totals']['claims']
            or workload.get('measured_quota_failures') != workload['accounting']['quota_boundary']['acquisition_failures']
            or workload.get('measured_quota_refusals') != workload['accounting']['quota_boundary']['acquisition_refusals']):
        raise ValueError('Oracle independently observed acquisition counts mismatch')
    if (any(type(workload.get(name)) is not int or workload[name] < 0
            for name in ('polls', 'empty_polls', 'selection_attempts', 'empty_selections'))
            or workload['polls'] != attempts + workload['empty_polls']
            or workload['selection_attempts'] != attempts + workload['empty_selections']):
        raise ValueError('Oracle empty-poll/selection accounting mismatch')


def optimization(root: Path):
    probe_path = root / 'model-sync-persistence-jdbc/target/qm/oracle-acquisition-probe.json'
    fresh(probe_path, root)
    held = json.loads(probe_path.read_text(encoding='utf-8'))
    if any(held.get(k) != v for k, v in current(root).items()):
        raise ValueError('Oracle acquisition proof source/run identity mismatch')
    probe(held)
    path = root / 'model-sync-persistence-jdbc/target/qm/oracle-optimization.json'
    fresh(path, root)
    value = json.loads(path.read_text(encoding='utf-8'))
    if (not isinstance(value, dict) or value.get('scope') != 'controlled-oracle-queue-comparison-not-production-capacity'
            or value.get('status') != 'PASS' or value.get('rows') != 100_000 or value.get('due_rows') != 100_000
            or value.get('workers') != 4 or value.get('pool') != 8 or value.get('owner_spacing_ms') != 20
            or value.get('owner_call_target') != 230 or value.get('cleanup_rows_remaining') != 0
            or value.get('quota_policy') != 'ORACLE_NOWAIT' or value.get('insert_policy') != 'ORACLE_26_NOWAIT'):
        raise ValueError('Oracle controlled comparison incomplete/failed or workload changed')
    variants = {'baseline', 'selector-only', 'admission-only', 'combined'}
    empty = value.get('empty_queue_results')
    if (not isinstance(empty, list) or len(empty) != 4 or not all(isinstance(v, dict) for v in empty)
            or {v.get('variant') for v in empty} != variants):
        raise ValueError('Oracle empty-queue comparison absent')
    for result in empty:
        if (result.get('polls') != 20 or result.get('empty_polls') != 20
                or any(result.get(k) != 0 for k in ('quota_grants', 'quota_denials', 'owner_calls',
                                                   'quota_acquisitions', 'quota_failures', 'quota_active'))):
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
                or result.get('polls') != result.get('ledger_attempts', 0) + result.get('empty_polls', 0)
                or result.get('selection_attempts') != result.get('ledger_attempts', 0) + result.get('empty_selections', 0)):
            raise ValueError('Oracle comparison outcome/accounting mismatch')
        accounting(result.get('accounting'), result['measured_owner_calls'], result['measured_completed_syncs'])
        accounting(result.get('warmup_accounting'), result['total_owner_calls']-result['measured_owner_calls'],
                   result['total_completed_syncs']-result['measured_completed_syncs'])
        drain(result.get('physical_drain'))
        if (result['ledger_attempts'] != result['accounting']['totals']['claims']
                or result.get('quota_failures') != result['accounting']['quota_boundary']['acquisition_failures']
                or result.get('quota_refusals') != result['accounting']['quota_boundary']['acquisition_refusals']
                or any(result.get(k) != result['accounting']['quota_boundary'][k]
                       for k in ('cooldown_attempts', 'cooldown_published', 'cooldown_failures'))):
            raise ValueError('Oracle observed boundary/claimed attempt mismatch')
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
        churn = {v: run[v]['quota_denials'] + run[v]['quota_failures'] for v in variants}
        if (churn['combined'] > churn['baseline'] or
                (churn['combined'] >= churn['selector-only'] if churn['selector-only'] else churn['combined'] != 0)):
            raise ValueError('Acquisition errors concealed comparison churn')
    return value


def worker(root: Path):
    path = root / 'model-sync-sample/target/qm/oracle-worker.json'
    fresh(path, root)
    value = json.loads(path.read_text(encoding='utf-8'))
    validate_worker(value)
    return value


def validate_worker(value):
    required = {'fixed-productive', 'fixed-empty', 'bounded-productive', 'bounded-empty',
                'bounded-future', 'local-urgent', 'independent-process-urgent',
                'cooldown-independent-owner-and-recovery', 'future-becomes-due', 'cooldown-expires'}
    if (not isinstance(value, dict) or value.get('status') != 'PASS'
            or value.get('scope') != 'actual-auto-configured-reactor-worker-not-million-row-capacity'
            or value.get('workers') != 4 or value.get('pool') != 8):
        raise ValueError('Actual worker evidence absent/failed or execution bounds changed')
    phases = value.get('phases')
    if (not isinstance(phases, list) or len(phases) != len(required)
            or not all(isinstance(p, dict) for p in phases) or {p.get('name') for p in phases} != required):
        raise ValueError('Required worker productive/idle/wakeup phases absent')
    for phase in phases:
        if (phase.get('status') != 'PASS'
                or phase.get('execution_mode') != 'auto-configured-ReactorSyncWorker-SmartLifecycle'
                or type(phase.get('elapsed_ms')) is not int or phase['elapsed_ms'] <= 0):
            raise ValueError('Worker phase failed, unexecuted or malformed')
        counts = phase.get('scheduling_delta')
        if not isinstance(counts, dict) or any(type(counts.get(k)) is not int or counts[k] < 0
                for k in ('ticks', 'empty', 'deferred', 'failed', 'fetched', 'cancelled',
                          'selector_callbacks', 'quota_grants', 'quota_denials', 'owner_calls', 'completed_syncs')):
            raise ValueError('Worker scheduling/admission/outcome counters absent')
        counters(phase.get('session_delta'))
        sessions = phase.get('sessions_before')
        if (phase.get('session_attribution') != 'VALID' or not isinstance(sessions, list)
                or len(sessions) != 8 or len(set(sessions)) != 8 or sessions != phase.get('sessions_after')):
            raise ValueError('Worker native session continuity unproven')
        if counts['failed']:
            raise ValueError('Benign worker probe reported infrastructure/owner failures')
        fixed = phase['name'].startswith('fixed-')
        if (phase.get('poll_interval_ms') != 250 or phase.get('productive_interval_ms') != (250 if fixed else 5)
                or phase.get('maximum_idle_interval_ms') != (250 if fixed else 1000)):
            raise ValueError('Worker runtime pacing configuration changed')
        if phase['name'].endswith('productive'):
            if any(counts[k] != 160 for k in ('completed_syncs', 'owner_calls', 'quota_grants')):
                raise ValueError('Worker backlog not completed; HTTP/claim acceptance is insufficient')
        if phase['name'] in {'fixed-empty', 'bounded-empty', 'bounded-future'}:
            if any(counts[k] for k in ('owner_calls', 'quota_grants', 'quota_denials')):
                raise ValueError('Idle worker spent provider admission')
            if (counts['ticks'] > (18 if fixed else 6) * phase['elapsed_ms'] / 1000 + 4
                    or counts['selector_callbacks'] > 9 * counts['ticks'] + 36):
                raise ValueError('Worker idle poll/selector budget exceeded')
            if not fixed and (type(phase.get('logical_gets_per_second')) not in (int, float)
                    or not 0 <= phase['logical_gets_per_second'] <= 100000):
                raise ValueError('Worker idle native-work budget exceeded')
        if phase['name'] in {'local-urgent', 'independent-process-urgent', 'future-becomes-due', 'cooldown-expires'}:
            latency = phase.get('wakeup_latency_upper_ms')
            expected = 2 if phase['name'] == 'cooldown-expires' else 1
            if type(latency) is not int or latency < 0 or latency > 2500 or counts['completed_syncs'] != expected:
                raise ValueError('Worker urgent committed completion/wakeup bound absent')
        if phase['name'] == 'independent-process-urgent' and phase.get('enqueue_process_exit') != 0:
            raise ValueError('Independent enqueue process did not complete')
        if phase['name'] == 'cooldown-independent-owner-and-recovery':
            if (counts['owner_calls'] != 1 or counts['completed_syncs'] != 1
                    or counts['deferred'] < 1 or counts['quota_denials'] < 1
                    or any(type(phase.get(k)) is not int or not 0 <= phase[k] <= 2500
                           for k in ('other_owner_wakeup_ms', 'recovery_wakeup_upper_ms'))):
                raise ValueError('Worker cooldown owner isolation/recovery proof absent')
