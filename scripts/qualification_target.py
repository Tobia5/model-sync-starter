"""Named configuration inventory and fresh runtime proof; no all-configuration qualification."""
import hashlib
import json
import re
from pathlib import Path
import xml.etree.ElementTree as ET
from run_evidence import current, fresh

ROOT = Path(__file__).resolve().parents[1]
TARGET = 'oracle26-nowait-ledger-first-v1'
TAG = 'modelsync-driver-characterization'


def manifest():
    value = json.loads((ROOT / 'docs/quality/qualification-target.json').read_text(encoding='utf-8'))
    if value.get('target') != TARGET or value.get('claim_scope') != 'named-configuration-only':
        raise ValueError('Qualification target must be the reviewed named configuration only')
    return value


def statement_body(text):
    start = text.index('void realStatementLockWaitCancelsAndRollsBack()')
    left = text.index('{', start)
    depth, end = 1, left + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[left:end].replace('\r\n', '\n')


def audit_source(root=ROOT):
    value = manifest()
    io = (root / 'model-sync-sample/src/test/java/io/modelsync/sample/OracleIoIT.java').read_text(encoding='utf-8')
    if hashlib.sha256(statement_body(io).encode()).hexdigest() != value['characterization']['body_sha256']:
        raise ValueError('Preserved plain-UPDATE characterization body/assertions changed')
    if f'@Tag("{TAG}")\n  @Test void {value["characterization"]["method"]}' not in io:
        raise ValueError('Only the reviewed exact mechanism test may carry the characterization tag')
    tagged = [(p, p.read_text(encoding='utf-8').count(f'@Tag("{TAG}")')) for p in root.glob('model-sync-*/src/**/*.java')]
    if sum(count for _, count in tagged) != 1:
        raise ValueError('Characterization exclusion must identify exactly one method')
    factory = (root / 'model-sync-persistence-jdbc/src/test/java/io/modelsync/jdbc/OracleQualificationStores.java').read_text(encoding='utf-8')
    if ('new JdbcWorkStore(jdbc, JdbcPointLockPolicy.ORACLE_NOWAIT, JdbcInsertPolicy.ORACLE_26_NOWAIT)' not in factory
            or 'new JdbcQuotaGate(jdbc, manager, clock, JdbcQuotaLockPolicy.ORACLE_NOWAIT, JdbcInsertPolicy.ORACLE_26_NOWAIT)' not in factory):
        raise ValueError('Full JDBC candidate factory fell back from the named policies')
    for name in ('OracleCapacityIT', 'OracleOptimizationIT', 'OracleScaleIT', 'OracleSmokeIT'):
        source = (root / f'model-sync-persistence-jdbc/src/test/java/io/modelsync/jdbc/{name}.java').read_text(encoding='utf-8')
        if 'new JdbcWorkStore(' in source or 'new JdbcQuotaGate(' in source or 'OracleQualificationStores.work(' not in source:
            raise ValueError(f'Full JDBC candidate bypassed the named policy factory: {name}')
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    pom = ET.parse(root / 'pom.xml').getroot()
    config = pom.find("m:profiles/m:profile[m:id='full']/m:build/m:plugins/m:plugin[m:artifactId='maven-failsafe-plugin']/m:configuration", ns)
    if (config is None or config.findtext('m:excludedGroups', namespaces=ns) != TAG
            or config.findtext('m:systemPropertyVariables/m:modelsync.qualification.target', namespaces=ns) != TARGET
            or config.findtext('m:environmentVariables/m:MODELSYNC_QUALIFICATION_TARGET', namespaces=ns) != TARGET
            or config.find('m:excludes', ns) is not None):
        raise ValueError('Full must select the named target and only the exact characterization tag')
    for module, suites in value['mandatory'].items():
        package = 'jdbc' if module.endswith('jdbc') else 'sample'
        folder = root / module / f'src/test/java/io/modelsync/{package}'
        for name, required in suites.items():
            files = ([folder / 'AtomicityChecks.java', folder / 'OracleAtomicityIT.java']
                     if name in {'OracleAtomicityIT', 'NowaitOracleAtomicityIT'} else
                     [folder / 'QueueClaimChecks.java'] if name == 'OracleSelectionIT' else [folder / f'{name}.java'])
            methods = [method for path in files for method in re.findall(r'@Test\s+(?:public\s+)?void\s+(\w+)\s*\(', path.read_text(encoding='utf-8'))]
            if name == 'OracleIoIT': methods.remove(value['characterization']['method'])
            if sorted(methods) != sorted(required) or not required:
                raise ValueError(f'Mandatory source inventory mismatch: {name}')
    return value


def validate_host(receipt, value=None):
    value = value or manifest()
    if receipt.get('qualification_target') != TARGET or receipt.get('claim_scope') != 'named-configuration-only':
        raise ValueError('Receipt cannot qualify DEFAULT, all configurations or another target')
    if any(receipt.get(key) != expected for key, expected in value['policies'].items()):
        raise ValueError('Named bounded-policy fixture fell back to DEFAULT or another policy')
    if (receipt.get('scope') != 'named-qualification-host' or receipt.get('status') != 'PASS'
            or any(receipt.get(key) is not True for key in
                   ('actual_adapter_settings_verified', 'qualified_mapping_consumed', 'datasource_enlisted'))
            or receipt.get('writer_protocol') != 'ledger-first'):
        raise ValueError('Actual adapter/mapping/enlistment proof absent')
    platform = value['platform']
    if (any(receipt.get(key) != platform[key] for key in ('oracle_major', 'oracle_minor', 'driver', 'hibernate'))
            or platform['oracle_brand'] not in str(receipt.get('oracle')) or platform['oracle_version'] not in str(receipt.get('oracle'))):
        raise ValueError('Named Oracle26/23.26 pinned platform mismatch')


def collect(root=ROOT):
    run = current(root)
    receipts = []
    for path in (root / 'model-sync-sample/target/qm').glob('qualification-host-*.json'):
        fresh(path, root)
        receipt = json.loads(path.read_text(encoding='utf-8'))
        if not isinstance(receipt, dict) or any(receipt.get(key) != run[key] for key in
                ('run_id', 'started_ns', 'source_sha', 'source_tree', 'source_change_digest')):
            raise ValueError('Named host receipt source/run mismatch')
        validate_host(receipt)
        receipts.append(receipt)
    return {'qualification_target': TARGET, 'claim_scope': 'named-configuration-only',
            'status': 'PASS' if set(manifest()['required_host_roles']) <= {r['role'] for r in receipts} else 'NOT_RUN' if not receipts else 'FAILED',
            'receipts': receipts, 'required_inventory': manifest()['mandatory']}


def qualify(root=ROOT):
    result = collect(root)
    if result['status'] != 'PASS':
        raise ValueError('Named configuration runtime roles absent/incomplete')
    return result


def characterization(faults):
    result = {'historical_capability': 'FAILED', 'historical_source': '2c39b75', 'historical_report': '43040e7',
              'current_status': 'NOT_RUN', 'applicability': 'driver characterization; not mandatory for the named protected paths'}
    if isinstance(faults, dict):
        for case in faults.get('cases', []):
            if case.get('name') == 'statement-lock-timeout': result['current_status'] = case['status']
    return result


if __name__ == '__main__':
    audit_source()
    print('NAMED TARGET SOURCE/INVENTORY PASS: one preserved characterization; mandatory suites retained')
