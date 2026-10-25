"""Cheap corruption tests for measured boundary/attempt evidence, never Oracle substitutes."""
import json
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from comparison_accounting import validate, drain, fact, publication, BOUNDARY, FIELDS


def receipt():
    a = {key: 0 for key in (*FIELDS, 'violations', 'acquisition_active', 'owner_active',
                           'cooldown_active', 'fence_rejections', 'local_conflicts')}
    a.update(id=1, phase='measurement', returned=True, terminal='RECONCILED', faults={},
             claims=1, acquisitions=1, grants=1, owner_calls=1, admission_uses=1, completions=1)
    before = dict.fromkeys(BOUNDARY, 0)
    after = dict(before, acquisitions=1, grants=1)
    totals = {key: a[key] for key in FIELDS}
    totals.update(committed_reconciliations=1, no_acquisition_claims=0)
    return dict(schema=1, accounting_id='11111111-1111-1111-1111-111111111111', phase='measurement',
                correlation_limit=65536, active_attempts=0, started_ns=1, stop_admission_ns=1000001,
                drained_ns=2000001, drain_ms=1, completed_during_drain=0, attempts=[a], totals=totals,
                quota_boundary_before=before, quota_boundary_after=after, quota_boundary=after.copy(),
                terminal_counts={'RECONCILED': 1})


class ComparisonEvidenceTests(unittest.TestCase):
    def test_publication_final_boundary_rejects_lost_pending_drift_and_missing_raw_attempts(self):
        import copy
        event=dict(operation_id='a'*64,requested_deadline_ms=100,canonical_deadline_ms=100,state='DURABLE_PENDING',durable=True)
        value=dict(status='PASS',unresolved=0,receipts=[event],persisted_rows=[dict(OPERATION_ID='a'*64,DEADLINE_MS=100,PUBLICATION_STATUS='APPLIED')])
        counts={p+'_'+s:0 for p in ('accept','apply') for s in ('attempts','active','commits','rollbacks','completion_failures','refusals','other_sql_failures')}
        after=dict(counts,accept_attempts=1,accept_commits=1,apply_attempts=4,apply_rollbacks=4,apply_refusals=4)
        physical=dict(before=counts,after=after,delta=after.copy(),detached_sql_failures=[dict(category='KNOWN',exception_type='org.springframework.dao.CannotAcquireLockException',sql_state='61000',vendor_code=54,truncated=False,count=4)])
        publication(value,physical)
        for mutation in ('pending','missing','drift','unrecorded','duplicate'):
            bad=copy.deepcopy(value)
            if mutation=='pending':bad['persisted_rows'][0]['PUBLICATION_STATUS']='PENDING'
            if mutation=='missing':bad['persisted_rows']=[]
            if mutation=='drift':bad['receipts'][0]['canonical_deadline_ms']=200
            if mutation=='unrecorded':bad['receipts'][0]['durable']=False
            if mutation=='duplicate':bad['persisted_rows']*=2
            with self.assertRaises(ValueError):publication(bad,physical)
        for field in ('apply_refusals','apply_commits','apply_completion_failures','apply_active'):
            bad=copy.deepcopy(physical);bad['delta'][field]+=1
            with self.assertRaises(ValueError):publication(value,bad)

    def test_complete_independent_events_qualify(self):
        validate(receipt(), 1, 1)

    def test_missing_or_double_events_do_not_qualify(self):
        for key in FIELDS:
            for bad in (None, 2):
                value = receipt()
                value['attempts'][0][key] = bad
                with self.assertRaises(ValueError, msg=key):
                    validate(value, 1, 1)
        value = receipt()
        value['attempts'].clear()
        with self.assertRaises(ValueError):
            validate(value, 1, 1)

    def test_boundary_counts_cannot_be_reconstructed_from_remainder(self):
        value = receipt()
        value['quota_boundary']['acquisition_failures'] = 1
        with self.assertRaises(ValueError):
            validate(value, 1, 1)
        value = receipt()
        value['quota_boundary_after']['acquisitions'] = 2
        with self.assertRaises(ValueError):
            validate(value, 1, 1)

    def test_unknown_uncertain_or_unrestricted_faults_are_rejected(self):
        good = dict(category='LOCAL_CONTENTION', exception_type='io.modelsync.core.port.LocalContention',
                    sql_state='61000', vendor_code=54, truncated=False)
        fixture=json.loads((Path(__file__).parent/'fixtures/acquisition-refusal.json').read_text())
        self.assertEqual(fixture,good)
        fact(fixture)
        for key, bad in [('category', 'UNKNOWN'), ('category', 'UNCERTAIN_COMMIT'),
                         ('truncated', True), ('sql_state', 'arbitrary secret'), ('message', 'secret')]:
            value = dict(good)
            value[key] = bad
            with self.assertRaises(ValueError):
                fact(value)
        value = receipt()
        value['attempts'][0]['faults']['FAILURE_METADATA'] = good
        with self.assertRaises(ValueError):
            validate(value, 1, 1)

    def test_warmup_and_drain_intervals_must_be_continuous_and_physically_idle(self):
        for key, bad in [('active_attempts', 1), ('drained_ns', 0), ('drain_ms', 0)]:
            value = receipt()
            value[key] = bad
            with self.assertRaises(ValueError):
                validate(value, 1, 1)
        value = receipt()
        value['attempts'][0]['phase'] = 'warmup'
        with self.assertRaises(ValueError):
            validate(value, 1, 1)
        physical = dict(worker_threads_terminated=True, physical_owner_active=0,
                        pool_active_connections=0, server_other_active_requests=0, server_transactions=0)
        drain(physical)
        for key in physical:
            bad = dict(physical, **{key: False if key == 'worker_threads_terminated' else 1})
            with self.assertRaises(ValueError):
                drain(bad)

    def test_owner_calls_and_committed_results_are_not_interchangeable(self):
        for calls, completed in ((0, 1), (2, 1), (1, 0), (1, 2)):
            with self.assertRaises(ValueError):
                validate(receipt(), calls, completed)

    def test_failed_cooldown_is_not_a_successful_publication(self):
        value = receipt()
        value['attempts'][0].update(cooldown_attempts=1, cooldown_published=0, cooldown_failures=1)
        with self.assertRaisesRegex(ValueError, 'cooldown'):
            validate(value, 1, 1)

    def test_comparison_launcher_is_narrow_and_keeps_full_entrypoint(self):
        from importlib.util import module_from_spec, spec_from_file_location
        root = Path(__file__).resolve().parents[2]
        spec = spec_from_file_location('accounting_launcher', root / 'scripts/full-oracle-container.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        runner = (root / 'model-sync-sample/src/test/java/io/modelsync/sample/OracleDiagnosticRunner.java').read_text()
        self.assertIn('case "comparison-accounting" -> List.of("io.modelsync.jdbc.OracleOptimizationIT");', runner)
        self.assertEqual(['-Pfull', 'clean', 'verify'], module.maven_command()[-3:])
        self.assertEqual(['-Pfull', '-N', 'initialize'], module.maven_command(True)[-3:])
        with patch.object(module, 'current', side_effect=ValueError('missing marker')):
            with self.assertRaisesRegex(ValueError, 'missing marker'):
                module.diagnostic_command(accounting=True)


if __name__ == '__main__':
    unittest.main()
