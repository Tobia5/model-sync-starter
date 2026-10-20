#!/usr/bin/env python3
"""Fail full qualification on absent, skipped or inadequate raw evidence."""
from collections import Counter
from pathlib import Path
import json
import sys
import xml.etree.ElementTree as ET
from run_evidence import current, fresh

ROOT=Path(__file__).resolve().parents[1]
SIMULATIONS={'fresh','hot','unsafe','event','cooldown','leases'}
ORACLE={'OracleSmokeIT','OracleConcurrencyIT','OracleMigrationIT','OracleScaleIT','OracleCapacityIT'}

def pit_counts(root=ROOT):
    path=root/'model-sync-testkit/target/pit-reports/mutations.xml'
    if not path.is_file(): raise ValueError('PIT XML absent')
    fresh(path,root)
    mutations=ET.parse(path).getroot().iter('mutation')
    counts=Counter(m.get('status','UNKNOWN') for m in mutations)
    total=sum(counts.values())
    if not total: raise ValueError('PIT generated zero mutations')
    score=100*counts['KILLED']/total
    if score<85: raise ValueError(f'PIT mutation score {score:.2f}% below 85%; raw counts={dict(counts)}')
    if counts['TIMED_OUT'] or counts['RUN_ERROR'] or counts['UNKNOWN']:
        raise ValueError(f'PIT timeout/run error/unknown results need investigation: {dict(counts)}')
    if not (root/'model-sync-testkit/target/pit-reports/index.html').is_file():
        raise ValueError('PIT HTML absent')
    fresh(root/'model-sync-testkit/target/pit-reports/index.html',root)
    return counts,score

def check(root=ROOT):
    current(root)
    sys.path.insert(0,str(root/'scripts'))
    from importlib.util import module_from_spec, spec_from_file_location
    spec=spec_from_file_location('model_sync_test_reports',Path(__file__).resolve().parent/'check-test-reports.py')
    report_module=module_from_spec(spec)
    spec.loader.exec_module(report_module)
    modules,tests=report_module.check(root)
    if tests<=0 or modules<=0: raise ValueError('Default suites absent')
    performance_test=root/'model-sync-performance/target/surefire-reports/TEST-io.modelsync.performance.PerformanceArchitectureTest.xml'
    fresh(performance_test,root)
    performance_suite=ET.parse(performance_test).getroot()
    if int(performance_suite.get('tests','0'))<1 or any(int(performance_suite.get(k,'0')) for k in ('errors','failures','skipped')):
        raise ValueError('Full-only compiled architecture test absent, failed or skipped')
    found=set()
    for report in (root/'model-sync-persistence-jdbc/target/failsafe-reports').glob('TEST-*.xml'):
        fresh(report,root)
        suite=ET.parse(report).getroot()
        name=(suite.get('name') or '').rsplit('.',1)[-1]
        total=int(suite.get('tests','0'))
        bad=sum(int(suite.get(x,'0')) for x in ('errors','failures','skipped'))
        if total and bad==0: found.add(name)
    if ORACLE-found: raise ValueError(f'Oracle suites missing, empty, failed or skipped: {sorted(ORACLE-found)}')
    counts,score=pit_counts(root)
    load={}
    for mode in SIMULATIONS:
        path=root/'model-sync-performance/target/qm'/f'load-{mode}.json'
        if not path.is_file(): raise ValueError(f'Gatling owner/ledger evidence absent: {mode}')
        fresh(path,root)
        data=json.loads(path.read_text(encoding='utf-8'))
        if data.get('scenario')!=mode or data.get('status')!='PASS':
            raise ValueError(f'Gatling scenario did not pass: {mode}')
        directory=root/'model-sync-performance/target/gatling'
        reports=[p for p in directory.glob(f'*{mode}*') if (p/'index.html').is_file() and (p/'simulation.log').is_file()]
        if not reports: raise ValueError(f'Gatling raw HTML/log absent: {mode}')
        for report in reports:
            fresh(report/'index.html',root)
            fresh(report/'simulation.log',root)
        load[mode]=data
    if load['leases'].get('ledger_rows',0)<100 or load['leases'].get('completed_rows',0)<1:
        raise ValueError('Multi-worker local lease evidence absent')
    scale=root/'model-sync-persistence-jdbc/target/qm/oracle-scale.json'
    if not scale.is_file(): raise ValueError('Oracle scale and database-work evidence absent')
    fresh(scale,root)
    capacity=json.loads(scale.read_text(encoding='utf-8'))
    if capacity.get('rows',0)<100_000 or capacity.get('ledger_claims') != 1 or capacity.get('db_work_per_ledger_claim') is None:
        raise ValueError('Oracle scale report lacks required dataset/work measurements')
    workload=root/'model-sync-persistence-jdbc/target/qm/oracle-workload.json'
    if not workload.is_file(): raise ValueError('Oracle-backed million-record reconciliation evidence absent')
    fresh(workload,root)
    measured=json.loads(workload.read_text(encoding='utf-8'))
    if (measured.get('scope')!='oracle-backed-reconciliation' or measured.get('rows',0)<1_000_000
            or measured.get('workers',0)<2 or measured.get('completed_syncs',0)<150
            or measured.get('completed_syncs')!=measured.get('projection_rows')
            or measured.get('physical_owner_calls',0)<230 or measured.get('db_session_work') is None
            or not isinstance(measured.get('run_one_latency_ms'),dict)
            or not isinstance(measured.get('reconciliation_lag_ms'),dict)):
        raise ValueError('Oracle capacity report lacks completed projections, owner calls, lag or database work')
    return {'junit_tests':tests,'oracle_suites':sorted(found),'pit_counts':dict(counts),
            'pit_score':score,'gatling':load,'oracle_scale':capacity,'oracle_workload':measured}

if __name__=='__main__':
    try: result=check()
    except (ValueError,OSError,ET.ParseError,json.JSONDecodeError) as error:
        print(f'FULL QUALIFICATION FAILED/BLOCKED: {error}',file=sys.stderr)
        raise SystemExit(1)
    print('FULL QUALIFICATION PASS:',json.dumps(result,sort_keys=True))
