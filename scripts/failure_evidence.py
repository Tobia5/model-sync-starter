"""Collect source-bound fault measurements independently of their qualification result."""
import json
from pathlib import Path
from run_evidence import current, fresh

SCOPES = {
    'oracle-recovery': ('independent-jvm-oracle-jpa-crash-recovery', {
        'CLAIM_COMMITTED', 'OWNER_RESPONDED', 'PROJECTION_FLUSHED', 'COMPLETION_COMMITTED',
        'OWNER_RESPONDED-new-generation', 'OWNER_RESPONDED-local-revision'}),
    'oracle-io': ('bounded-owned-oracle-transport-and-shutdown', {
        'pool-exhaustion', 'statement-lock-timeout', 'transaction-deadline', 'database-read-stall',
        'connection-refused', 'database-login-stall', 'graceful-owner-drain',
        'late-owner-and-bounded-stop', 'owner-socket-timeout', 'locked-jpa-shutdown',
        'uncertain-before-commit', 'uncertain-after-commit'})}


def collect(root: Path, name: str):
    path = root / f'model-sync-sample/target/qm/{name}.json'
    if not path.exists():
        return None
    run = current(root)
    fresh(path, root)
    value = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(value, dict) or value.get('scope') != SCOPES[name][0]:
        raise ValueError(f'Malformed fault evidence: {name}')
    if any(value.get(key) != run[key] for key in ('run_id', 'started_ns', 'source_sha', 'source_tree', 'source_change_digest')):
        raise ValueError(f'Fault evidence source/run mismatch: {name}')
    cases = value.get('cases')
    if value.get('status') not in {'PASS', 'FAILED'} or not isinstance(cases, list):
        raise ValueError(f'Malformed fault evidence status/cases: {name}')
    if any(not isinstance(case, dict) or case.get('name') not in SCOPES[name][1]
           or case.get('status') not in {'PASS', 'FAILED'} for case in cases):
        raise ValueError(f'Malformed fault case: {name}')
    return value


def qualify(root: Path):
    result = {}
    for name, (_, required) in SCOPES.items():
        value = collect(root, name)
        if value is None or value['status'] != 'PASS':
            raise ValueError(f'Required fault measurements missing/failed: {name}')
        cases = value['cases']
        if len(cases) != len(required) or {case['name'] for case in cases} != required or any(case['status'] != 'PASS' for case in cases):
            raise ValueError(f'Required fault cases absent, duplicated or failed: {name}')
        if value.get('owned_pool_closed') is not True:
            raise ValueError(f'Owned fault pool cleanup absent: {name}')
        if name == 'oracle-recovery':
            for case in cases:
                if (case.get('child_absent') is not True or case.get('obsolete_completion_rejected') is not True
                        or not isinstance(case.get('after_crash'), dict) or not isinstance(case.get('after_recovery'), dict)
                        or case.get('recovery_ms', 30001) > 30000):
                    raise ValueError('Process termination/recovery/fencing proof absent')
        else:
            if value.get('owned_executor_terminated') is not True:
                raise ValueError('Owned I/O executor not terminated')
            by_name = {case['name']: case for case in cases}
            late = by_name['late-owner-and-bounded-stop']
            if (late.get('initial_stop', {}).get('status') != 'TIMED_OUT'
                    or late.get('initial_stop', {}).get('physicalOperations', 0) < 1
                    or late.get('final_stop', {}).get('status') != 'DRAINED'
                    or late.get('successful_applications') != 0 or late.get('client_active_after') != 0):
                raise ValueError('Late physical owner/drain distinction absent')
            if any(by_name[key].get('projection_and_success_atomic') is not True
                   for key in ('uncertain-before-commit', 'uncertain-after-commit')):
                raise ValueError('Uncertain COMMIT atomic observation absent')
        result[name] = value
    return result
