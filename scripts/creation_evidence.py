"""First-creation observations remain separate from qualification of conflict controls."""
import json
from run_evidence import current, fresh

DEFAULT = {f'{kind}-default-{resolution}' for kind in ('work', 'quota') for resolution in ('commit', 'rollback')}
NATIVE = {f'{kind}-native-{resolution}' for kind in ('work', 'quota') for resolution in ('commit', 'rollback')}
FENCED = {'projection-state-fence-commit', 'projection-state-fence-rollback'}
CASES = DEFAULT | NATIVE | FENCED

def collect(root):
    path = root / 'model-sync-sample/target/qm/oracle-first-creation.json'
    if not path.is_file(): return None
    run=current(root);fresh(path,root)
    data=json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(data,dict) or data.get('scope')!='compliant-first-creation' or data.get('status') not in {'PASS','FAILED'}:
        raise ValueError('Malformed first-creation evidence')
    if any(data.get(k)!=run[k] for k in ('run_id','started_ns','source_sha','source_tree','source_change_digest')):
        raise ValueError('First-creation source/run mismatch')
    if not isinstance(data.get('cases'),list) or any(not isinstance(c,dict) or c.get('name') not in CASES or c.get('status') not in {'PASS','FAILED'} for c in data['cases']):
        raise ValueError('Malformed first-creation cases')
    return data

def qualify(root):
    data=collect(root)
    if data is None or data['status']!='PASS' or len(data['cases'])!=len(CASES):
        raise ValueError('Required first-creation evidence absent or incomplete')
    cases={c['name']:c for c in data['cases']}
    if set(cases)!=CASES or any(c.get('status')!='PASS' or c.get('cleanup_verified') is not True or c.get('emergency_release') is not False or c.get('physical_remaining')!=0 for c in cases.values()):
        raise ValueError('First-creation cases failed, duplicated or not cleaned')
    if data.get('owned_pools_closed') is not True or data.get('owned_executor_terminated') is not True or data.get('physical_remaining')!=0 or data.get('table_removed') is not True:
        raise ValueError('First-creation physical cleanup missing')
    for name in DEFAULT:
        c=cases[name]
        if c.get('classification')!='UNBOUNDED_DEFAULT_WAIT' or c.get('b_physically_complete_before_a_resolution') is not False or c.get('b_while_a_unresolved',{}).get('LOCK_WAIT')!=1:
            raise ValueError('Default insert wait was not honestly observed')
    for name in NATIVE|FENCED:
        c=cases[name];b=c.get('b_before_a_resolution',{})
        before,after=b.get('server_before_rollback',{}),b.get('server_after_transaction',{})
        if (b.get('oracle_code')!=54 or b.get('completion')!='ROLLED_BACK'
                or type(b.get('physical_return_ms')) is not int or not 0<=b['physical_return_ms']<=5000
                or before.get('LOCK_WAIT')!=0 or after.get('LOCK_WAIT')!=0 or after.get('TRANSACTION_ACTIVE')!=0
                or c.get('b_failed_transaction_accepted') is not False
                or any(type(before.get(k)) is not int or before[k]<1 or before[k]!=after.get(k) for k in ('SID','SERIAL'))):
            raise ValueError('First-creation refusal/rollback proof missing: '+name)
        expected='SERIALIZED_BY_WORK_FENCE' if name in FENCED else 'MEASURED_INSERT_REFUSAL'
        if c.get('classification')!=expected or (name in FENCED and c.get('b_mapping_entered') is not False):
            raise ValueError('First-creation path classification missing')
    return data
