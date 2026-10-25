"""Strict admission of bounded fixture events, independent physical and committed outcomes."""
import re
from collections import Counter

FIELDS = ('claims', 'acquisitions', 'grants', 'denials', 'acquisition_failures',
          'owner_calls', 'admission_uses', 'completions', 'reschedules',
          'cooldown_attempts', 'cooldown_published', 'cooldown_failures')
BOUNDARY = ('acquisitions', 'grants', 'denials', 'acquisition_failures',
            'acquisition_refusals', 'acquisition_known_failures', 'acquisition_unknown_failures',
            'acquisition_uncertain_commits', 'acquisition_active',
            'cooldown_attempts', 'cooldown_published', 'cooldown_failures', 'cooldown_active')


def require(condition, text):
    if not condition:
        raise ValueError('Comparison accounting: ' + text)


def number(value, key):
    n = value.get(key)
    require(type(n) is int and n >= 0, 'missing/nonnegative integer: ' + key)
    return n


def fact(value):
    require(isinstance(value, dict) and set(value) ==
            {'category', 'exception_type', 'sql_state', 'vendor_code', 'truncated'}, 'unsafe/missing detached facts')
    require(isinstance(value['exception_type'], str) and re.fullmatch(r'[A-Za-z0-9_.$]{1,160}', value['exception_type']),
            'invalid detached type')
    state = value['sql_state']
    require(state is None or isinstance(state, str) and re.fullmatch(r'[A-Za-z0-9]{5}', state), 'invalid SQLState')
    require(value['vendor_code'] is None or type(value['vendor_code']) is int, 'invalid vendor code')
    require(value['truncated'] is False and value['category'] in {'LOCAL_CONTENTION', 'KNOWN', 'LEASE_LOST'},
            'unknown/uncertain/truncated failure')


def validate(value, physical_calls, committed_syncs):
    require(isinstance(value, dict) and value.get('schema') == 1, 'schema absent')
    require(isinstance(value.get('accounting_id'), str) and re.fullmatch(r'[a-f0-9-]{36}', value['accounting_id']),
            'run-local correlation identity absent')
    started, stop, drained = (number(value, key) for key in ('started_ns', 'stop_admission_ns', 'drained_ns'))
    require(started > 0 and started <= stop <= drained and number(value, 'active_attempts') == 0,
            'phase not drained')
    require(number(value, 'drain_ms') == (drained - stop) // 1_000_000, 'drain boundary mismatch')
    attempts = value.get('attempts')
    require(isinstance(attempts, list) and len(attempts) <= number(value, 'correlation_limit') <= 65_536,
            'attempt bound absent/exceeded')
    totals, terminals, ids = Counter(), Counter(), set()
    no_acquisition = 0
    for a in attempts:
        require(isinstance(a, dict), 'malformed attempt')
        identity = number(a, 'id')
        require(identity > 0 and identity not in ids and a.get('phase') == value.get('phase'), 'duplicate/cross-phase event')
        ids.add(identity)
        for key in FIELDS:
            totals[key] += number(a, key)
        require(all(number(a, k) == 0 for k in ('violations', 'acquisition_active', 'owner_active', 'cooldown_active')),
                'active/invalid admission operation')
        c, q, g, d, f = (number(a, k) for k in ('claims', 'acquisitions', 'grants', 'denials', 'acquisition_failures'))
        complete, retry = number(a, 'completions'), number(a, 'reschedules')
        require(c <= 1 and q <= c and q == g + d + f, 'acquisition cardinality/partition')
        require(number(a, 'owner_calls') == g == number(a, 'admission_uses'), 'owner/admission mismatch')
        require(number(a, 'cooldown_attempts') == number(a, 'cooldown_published') + number(a, 'cooldown_failures')
                and number(a, 'cooldown_failures') == 0, 'failed/missing cooldown publication')
        faults = a.get('faults')
        require(isinstance(faults, dict), 'detached fault events absent')
        for site, evidence in faults.items():
            require(site in {'PREPARE', 'ACQUIRE', 'OWNER', 'RESULT'}, 'unresolved metadata/escaped/unknown failure')
            fact(evidence)
            if site == 'ACQUIRE':
                require(evidence['category'] == 'LOCAL_CONTENTION' and evidence['vendor_code'] == 54,
                        'unconfirmed acquisition refusal')
        require((f == 1) == ('ACQUIRE' in faults), 'missing/double acquisition fault')
        t = a.get('terminal')
        patterns = {
            'EMPTY': c == q == complete == retry == 0 and not faults,
            'FRESH_NO_FETCH': c == complete == 1 and q == retry == 0 and not faults,
            'FENCED': c == 1 and number(a, 'fence_rejections') > 0 and complete == retry == 0,
            'PRE_ADMISSION_FAILED': q == complete == 0 and retry == 1 and set(faults) == {'PREPARE'},
            'ACQUISITION_FAILED': f == retry == 1 and complete == 0 and set(faults) == {'ACQUIRE'},
            'DENIED': d == retry == 1 and complete == 0 and not faults,
            'RECONCILED': g == complete == 1 and retry == 0 and not faults,
            'OWNER_FAILED': g == retry == 1 and complete == 0 and set(faults) == {'OWNER'},
            'RESULT_FAILED': g == retry == 1 and complete == 0 and set(faults) == {'RESULT'},
            'RESULT_DEFERRED': g == retry == 1 and complete == 0 and number(a, 'local_conflicts') == 1 and not faults,
        }
        require(patterns.get(t) is True, 'missing/contradictory terminal events')
        require(a.get('returned') is (c == 1) and (t != 'EMPTY') == (c == 1), 'claim/return mismatch')
        require((q == 0 and t in {'EMPTY', 'FRESH_NO_FETCH', 'PRE_ADMISSION_FAILED', 'FENCED'})
                or (q == 1 and t not in {'EMPTY', 'FRESH_NO_FETCH', 'PRE_ADMISSION_FAILED'}), 'missing acquisition event')
        no_acquisition += c == 1 and q == 0
        terminals[t] += 1
    boundary = value.get('quota_boundary')
    before, after = value.get('quota_boundary_before'), value.get('quota_boundary_after')
    require(all(isinstance(x, dict) for x in (boundary, before, after)), 'boundary snapshots absent')
    for key in BOUNDARY:
        expected = number(after, key) if key.endswith('active') else number(after, key) - number(before, key)
        require(number(boundary, key) == expected, 'boundary interval mismatch: ' + key)
        if key.endswith('active'):
            require(number(before, key) == number(after, key) == 0, 'boundary still active')
        if key in FIELDS:
            require(number(boundary, key) == totals[key], 'boundary/detail mismatch: ' + key)
    require(number(boundary, 'acquisition_failures') == sum(number(boundary, key) for key in
            ('acquisition_refusals', 'acquisition_known_failures', 'acquisition_unknown_failures', 'acquisition_uncertain_commits')),
            'failure classification partition')
    require(number(boundary, 'acquisition_failures') == number(boundary, 'acquisition_refusals'),
            'non-contention acquisition failures')
    recorded = value.get('totals')
    require(isinstance(recorded, dict), 'totals absent')
    for key in FIELDS:
        require(number(recorded, key) == totals[key], 'summary/detail mismatch: ' + key)
    require(value.get('terminal_counts') == dict(terminals), 'terminal summary mismatch')
    require(number(value, 'completed_during_drain') <= len(attempts), 'invalid drain outcomes')
    require(number(recorded, 'no_acquisition_claims') == no_acquisition
            and totals['claims'] == totals['acquisitions'] + no_acquisition, 'claim partition')
    require(totals['owner_calls'] == physical_calls, 'independent owner count mismatch')
    require(number(recorded, 'committed_reconciliations') == terminals['RECONCILED'] == committed_syncs,
            'independent committed reconciliation mismatch')


def drain(value):
    require(isinstance(value, dict) and value.get('worker_threads_terminated') is True, 'worker not terminated')
    for key in ('physical_owner_active', 'pool_active_connections', 'server_other_active_requests', 'server_transactions'):
        require(number(value, key) == 0, 'physical resource active: ' + key)


def probe(value):
    require(isinstance(value, dict) and value.get('status') == 'PASS'
            and value.get('scope') == 'one-row-acquisition-accounting-not-capacity'
            and value.get('quota_policy') == 'ORACLE_NOWAIT' and value.get('insert_policy') == 'ORACLE_26_NOWAIT',
            'held-quota proof absent/failed')
    require(value.get('owned_fixture_cleanup') is True and value.get('owned_executor_terminated') is True
            and value.get('pool_active_after') == 0, 'probe cleanup/pool unverified')
    cases = value.get('cases')
    require(isinstance(cases, list) and len(cases) == 2 and {c.get('name') for c in cases} == {'held-acquisition', 'held-cooldown'},
            'held-quota cases absent')
    for case in cases:
        require(case.get('status') == 'PASS' and case.get('blocker_released_for_proof') is False
                and number(case, 'client_return_ms') <= 5000 and case.get('guard_rolled_back') is True, 'blocker-held outcome absent')
        tx = case.get('transaction', {})
        require(tx.get('completion') == 'ROLLED_BACK' and isinstance(tx.get('actual_sql'), str)
                and tx['actual_sql'].lower().endswith('for update nowait')
                and 'MS_QUOTA' in tx['actual_sql'], 'actual quota SQL/rollback absent')
        for name, active in (('server_before_rollback', 1), ('server_after_rollback', 0)):
            state = tx.get(name, {})
            require(state.get('LOCK_WAIT') == 0 and state.get('TRANSACTION_ACTIVE') == active,
                    'exact server request/transaction state unverified')
        if case['name'] == 'held-acquisition':
            validate(case.get('accounting'), 0, 0)
            require(case['accounting']['quota_boundary']['acquisition_refusals'] == 1, 'forced refusal missing')
        else:
            fact(case.get('failure'))
            require(case['failure']['category'] == 'LOCAL_CONTENTION' and case['failure']['vendor_code'] == 54
                    and case.get('extension_persisted') is False, 'failed cooldown falsely persisted')
            require(all(case.get('counters', {}).get(k) == v for k, v in
                        {'cooldown_attempts': 1, 'cooldown_published': 0, 'cooldown_failures': 1, 'cooldown_active': 0}.items()),
                    'cooldown failure accounting mismatch')
    validate(value.get('recovery_accounting'), 1, 1)
    require(value.get('failed_extension_was_not_published') is True, 'publication recovery absent')
    require(all(value.get('cooldown_recovery_counters', {}).get(k) == v for k, v in
                {'cooldown_attempts': 3, 'cooldown_published': 2, 'cooldown_failures': 1}.items()), 'publication recovery counters')
