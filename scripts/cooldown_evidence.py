"""Fresh cooldown collection is independent of success; qualification inspects all required outcomes."""
import json
from run_evidence import current, fresh

CASES = {
    'heldQuotaReleaseWithinBudgetReusesAbsoluteDeadline',
    'budgetExhaustionRetainsSharedPendingAndOtherProviderProgress',
    'duplicateInsertRefusalIsUnrecordedUntilCompetitorCommitOrRollback',
    'durablePendingSurvivesForcedOwnedProcessTermination',
    'committedCooldownPrecedesFailedMetadataAndRecoveryDoesNotRefetch',
}


def collect(root):
    run = current(root)
    reports = {}
    for module, filename, scope in (
            ('model-sync-persistence-jdbc', 'oracle-cooldown', 'durable-cooldown-small-recovery'),
            ('model-sync-sample', 'oracle-cooldown-worker', 'actual-worker-durable-cooldown')):
        path = root / module / 'target/qm' / (filename + '.json')
        if not path.is_file():
            continue
        fresh(path, root)
        value = json.loads(path.read_text(encoding='utf-8'))
        if not isinstance(value, dict) or value.get('scope') != scope or value.get('status') not in {'PASS', 'FAILED'}:
            raise ValueError('Malformed cooldown evidence')
        if any(value.get(key) != run[key] for key in ('run_id','started_ns','source_sha','source_tree','source_change_digest')):
            raise ValueError('Cooldown source/run identity mismatch')
        reports[filename] = value
    return {'status':'PASS' if len(reports)==2 and all(r['status']=='PASS' for r in reports.values()) else 'FAILED','reports':reports} if reports else None


def qualify(root):
    value = collect(root)
    if not value or set(value['reports']) != {'oracle-cooldown','oracle-cooldown-worker'}:
        raise ValueError('Required cooldown execution evidence absent')
    small, worker = value['reports']['oracle-cooldown'], value['reports']['oracle-cooldown-worker']
    if (small.get('status') != 'PASS' or worker.get('status') != 'PASS'
            or small.get('cooldown_policy') != 'DURABLE'
            or small.get('owned_executor_terminated') is not True or small.get('owned_pools_closed') is not True):
        raise ValueError('Cooldown run failed or resources unverified')
    cases = small.get('cases')
    if not isinstance(cases,list) or len(cases) != len(CASES) or {c.get('name') for c in cases} != CASES:
        raise ValueError('Cooldown case inventory incomplete')
    for case in cases:
        if (case.get('status') != 'PASS' or case.get('runtime_pool_active') != 0
                or case.get('runtime_connection_healthy') is not True or case.get('owned_rows_remaining') != 0):
            raise ValueError('Cooldown outcome/cleanup not verified')
    indexed = {c['name']:c for c in cases}
    for name, count in [('heldQuotaReleaseWithinBudgetReusesAbsoluteDeadline',1),
                        ('budgetExhaustionRetainsSharedPendingAndOtherProviderProgress',4)]:
        case = indexed[name]
        if case.get('blocker_released_for_refusal_proof') is not False or case.get('guard_rolled_back') is not True:
            raise ValueError('Blocker-held rollback proof absent')
        txs = [t for t in case.get('refusal_transactions',[]) if t.get('completion') == 'ROLLED_BACK']
        if len(txs) != count or not 0 <= case.get('client_ms',-1) <= 5000:
            raise ValueError('Bounded refusal attempt count/timing absent')
        for tx in txs:
            if not str(tx.get('actual_sql','')).lower().endswith('for update nowait'):
                raise ValueError('Actual bounded quota SQL absent')
            for key,active in [('server_before_rollback',1),('server_after_rollback',0)]:
                state=tx.get(key,{})
                if state.get('LOCK_WAIT') != 0 or state.get('TRANSACTION_ACTIVE') != active:
                    raise ValueError('Exact server/transaction outcome absent')
    if any(worker.get(k) is not True for k in ('worker_still_running','recovery_without_extra_owner_read','owned_fixture_cleanup','owned_executor_terminated')):
        raise ValueError('Actual worker recovery/cleanup unqualified')
    if (worker.get('blocker_released_for_pending_proof') is not False or worker.get('physical_owner_calls') != 2
            or worker.get('local_committed_admissions') != 2 or worker.get('runtime_pool_active_after_stop') != 0
            or worker.get('shutdown',{}).get('status') != 'DRAINED' or worker.get('shutdown',{}).get('physicalOperations') != 0):
        raise ValueError('Actual worker physical admission/drain mismatch')
    return value
