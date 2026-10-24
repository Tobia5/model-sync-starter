"""Collect current partial quota diagnostics independently from strict qualification."""
import json
from run_evidence import current, fresh

CASES = {'default-compatible', 'held-acquire', 'held-cooldown',
         'coordinator-admission-refusal', 'owner-cooldown-refusal', 'registration-boundary'}
SCOPE = 'shared-quota-contention'


def collect(root):
    path = root / 'model-sync-sample/target/qm/oracle-quota-contention.json'
    if not path.is_file():
        return None
    run = current(root)
    fresh(path, root)
    data = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(data, dict) or data.get('scope') != SCOPE or data.get('status') not in {'PASS', 'FAILED'}:
        raise ValueError('Malformed quota evidence')
    if any(data.get(key) != run[key] for key in ('run_id', 'started_ns', 'source_sha', 'source_tree', 'source_change_digest')):
        raise ValueError('Quota source/run mismatch')
    cases = data.get('cases')
    if not isinstance(cases, list) or any(not isinstance(c, dict) or c.get('name') not in CASES
            or c.get('status') not in {'PASS', 'FAILED'} for c in cases):
        raise ValueError('Malformed quota cases')
    return data


def qualify(root):
    data = collect(root)
    if data is None or data['status'] != 'PASS' or len(data['cases']) != len(CASES):
        raise ValueError('Required quota evidence absent or failed')
    if {c['name'] for c in data['cases']} != CASES or any(c['status'] != 'PASS'
            or c.get('cleanup_verified') is not True or c.get('healthy_pools') is not True for c in data['cases']):
        raise ValueError('Quota cases absent, duplicated, failed or not cleaned')
    if data.get('owned_pools_closed') is not True or data.get('owned_executor_terminated') is not True or data.get('physical_remaining') != 0 or data.get('table_removed') is not True:
        raise ValueError('Quota physical cleanup not verified')
    cases = {c['name']: c for c in data['cases']}
    for name, count in (('held-acquire', 3), ('held-cooldown', 3), ('coordinator-admission-refusal', 1), ('owner-cooldown-refusal', 1)):
        case = cases[name]
        attempts = case.get('attempts')
        if (not isinstance(attempts, list) or len(attempts) != count
                or case.get('blocker_released_for_proof') is not False or case.get('emergency_release') is not False):
            raise ValueError('Missing quota refusal observations: ' + name)
        for attempt in attempts:
            if not isinstance(attempt, dict):
                raise ValueError('Malformed quota attempt')
            before, after = attempt.get('server_before_rollback', {}), attempt.get('server_after_rollback', {})
            if (not isinstance(before, dict) or not isinstance(after, dict)
                    or attempt.get('oracle_code') != 54 or attempt.get('completion') != 'ROLLED_BACK'
                    or attempt.get('guard_rolled_back_and_reacquired') is not True
                    or type(attempt.get('client_and_transaction_return_ms')) is not int
                    or not 0 <= attempt['client_and_transaction_return_ms'] <= 5000
                    or not str(attempt.get('server_sql', '')).lower().endswith('nowait')
                    or before.get('LOCK_WAIT') != 0 or before.get('TRANSACTION_ACTIVE') != 1
                    or after.get('LOCK_WAIT') != 0 or after.get('TRANSACTION_ACTIVE') != 0
                    or any(type(before.get(k)) is not int or before[k] < 1 or before[k] != after.get(k) for k in ('SID', 'SERIAL'))):
                raise ValueError('Quota database/rollback/resource proof missing: ' + name)
    if (cases['coordinator-admission-refusal'].get('owner_calls_before_release') != 0
            or cases['coordinator-admission-refusal'].get('completed_syncs') != 1
            or cases['owner-cooldown-refusal'].get('cooldown_persisted') is not False
            or cases['owner-cooldown-refusal'].get('cooldown_persisted_after_release') is not True
            or cases['owner-cooldown-refusal'].get('owner_calls') != 2
            or cases['owner-cooldown-refusal'].get('completed_syncs') != 1):
        raise ValueError('Quota admission/cooldown accounting missing')
    return data
