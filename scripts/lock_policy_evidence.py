"""Collect fresh production lock-policy results separately from qualification."""
import json
from run_evidence import current, fresh

SCOPE = 'production-oracle-point-lock-integration'
CASES = {'disabled-compatible', 'producer-refusal', 'result-refusal-recovery',
         'retry-metadata-refusal', 'uncontended-first-inserts', 'enabled-safety-controls',
         'actual-worker-contention-exhaustion'}


def collect(root):
    path = root / 'model-sync-sample/target/qm/oracle-lock-policy.json'
    if not path.is_file():
        return None
    run = current(root)
    fresh(path, root)
    data = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(data, dict) or data.get('scope') != SCOPE or data.get('status') not in {'PASS', 'FAILED'}:
        raise ValueError('Malformed lock-policy evidence')
    if any(data.get(key) != run[key] for key in ('run_id', 'started_ns', 'source_sha', 'source_tree', 'source_change_digest')):
        raise ValueError('Lock-policy source/run mismatch')
    cases = data.get('cases')
    if not isinstance(cases, list) or any(not isinstance(c, dict) or c.get('name') not in CASES
            or c.get('status') not in {'PASS', 'FAILED'} for c in cases):
        raise ValueError('Malformed lock-policy cases')
    return data


def qualify(root):
    data = collect(root)
    if data is None or data['status'] != 'PASS' or len(data['cases']) != len(CASES):
        raise ValueError('Required lock-policy evidence absent or failed')
    if {c['name'] for c in data['cases']} != CASES or any(c['status'] != 'PASS' or c.get('cleanup_verified') is not True for c in data['cases']):
        raise ValueError('Lock-policy cases absent, duplicated, failed or not cleaned')
    if data.get('owned_pools_closed') is not True or data.get('owned_executor_terminated') is not True or data.get('physical_remaining') != 0 or data.get('table_removed') is not True:
        raise ValueError('Lock-policy physical cleanup not verified')
    by_name = {c['name']: c for c in data['cases']}
    for name in ('producer-refusal', 'result-refusal-recovery', 'retry-metadata-refusal'):
        case = by_name[name]
        attempt = case.get('attempt', {})
        before = attempt.get('server_before_rollback', {})
        after = attempt.get('server_after_transaction', {})
        errors = attempt.get('error_chain', [])
        if (not isinstance(before, dict) or not isinstance(after, dict) or not isinstance(errors, list)
                or any(not isinstance(e, dict) for e in errors)
                or any(type(attempt.get(key)) is not int or not 0 <= attempt[key] <= 5000
                       for key in ('client_refusal_ms', 'transaction_end_ms'))
                or any(type(before.get(key)) is not int or before[key] < 1 for key in ('SID', 'SERIAL'))):
            raise ValueError('Malformed database refusal measurement: ' + name)
        if (case.get('blocker_released_for_proof') is not False or case.get('emergency_release') is not False
                or case.get('guard_write_rolled_back_and_lock_released') is not True
                or attempt.get('transaction_completion') != 1
                or before.get('LOCK_WAIT') != 0 or before.get('TRANSACTION_ACTIVE') != 1
                or after.get('LOCK_WAIT') != 0 or after.get('TRANSACTION_ACTIVE') != 0
                or before.get('SID') != after.get('SID') or before.get('SERIAL') != after.get('SERIAL')
                or not any(e.get('oracle_code') == 54 for e in errors)):
            raise ValueError('Database refusal/rollback/session proof missing: ' + name)
    worker = by_name['actual-worker-contention-exhaustion']
    if (worker.get('owner_calls') != 3 or worker.get('completed_syncs') != 0
            or worker.get('worker_physical_after_stop') != 0
            or worker.get('durable', {}).get('WORK_STATUS') != 'DEAD'
            or worker.get('durable', {}).get('LAST_ERROR_CODE') != 'LOCAL_CONTENTION'):
        raise ValueError('Bounded actual-worker contention outcome missing')
    return data
