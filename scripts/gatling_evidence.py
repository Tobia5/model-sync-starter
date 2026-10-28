"""Read current-run Gatling HTTP/assertion evidence independently of harness outcomes."""
import json
import math
import xml.etree.ElementTree as ET
from run_evidence import fresh

SIMULATIONS = {
    'fresh': 'freshvisitssimulation', 'hot': 'hotkeysimulation',
    'unsafe': 'unsafevaluesimulation', 'event': 'eventjobvisitsimulation',
    'cooldown': 'cooldownrecoverysimulation', 'leases': 'leasesledgersimulation',
}


def measured(root, mode):
    try:
        return _measured(root, mode)
    except (OSError, KeyError, TypeError, json.JSONDecodeError, ET.ParseError) as error:
        raise ValueError(f'Malformed Gatling evidence for {mode}: {error}') from error


def _measured(root, mode):
    directory = root / 'model-sync-performance/target/gatling'
    reports = list(directory.glob(SIMULATIONS[mode] + '-*'))
    if not reports:
        return None
    if len(reports) != 1:
        raise ValueError(f'Ambiguous Gatling reports: {mode}')
    report = reports[0]
    for name in ('index.html', 'simulation.log', 'js/stats.json', 'js/assertions.xml'):
        fresh(report / name, root)
    stats = json.loads((report / 'js/stats.json').read_text(encoding='utf-8'))['stats']
    requests = stats['numberOfRequests']
    if (any(type(requests[k]) is not int or requests[k] < 0 for k in ('total', 'ok', 'ko'))
            or not requests['total'] or requests['total'] != requests['ok'] + requests['ko']):
        raise ValueError(f'Malformed Gatling request counts: {mode}')
    assertions = ET.parse(report / 'js/assertions.xml').getroot()
    count = int(assertions.attrib['tests'])
    failures = int(assertions.attrib['failures'])
    errors = int(assertions.attrib['errors'])
    skipped = int(assertions.get('skipped', '0'))
    if count <= 0 or min(failures, errors, skipped) < 0 or failures + errors + skipped > count:
        raise ValueError(f'Malformed Gatling assertions: {mode}')
    latency = {key: stats[field]['total'] for key, field in
               (('p50', 'percentiles1'), ('p95', 'percentiles3'), ('p99', 'percentiles4'))}
    if any(type(v) not in (int, float) or not math.isfinite(v) or v < 0 for v in latency.values()):
        raise ValueError(f'Malformed Gatling latency: {mode}')
    return {'status': 'FAILED' if requests['ko'] or failures or errors or skipped else 'PASS',
            'requests': requests, 'http_response_time_ms': latency,
            'assertions': {'tests': count, 'failures': failures, 'errors': errors, 'skipped': skipped},
            'raw_report': report.relative_to(root).as_posix()}
