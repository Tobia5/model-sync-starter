import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import os
import time
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from failure_evidence import collect, qualify, SCOPES
from run_evidence import begin


class FailureEvidenceTests(unittest.TestCase):
    def fixture(self, directory):
        root = Path(directory)
        (root / '.gitignore').write_text('target/\n')
        for args in [('init',), ('config', 'user.email', 'synthetic@example.invalid'),
                     ('config', 'user.name', 'Synthetic'), ('commit', '--allow-empty', '-m', 'fixture')]:
            subprocess.run(['git', *args], cwd=root, check=True, capture_output=True)
        run = begin(root)
        path = root / 'model-sync-sample/target/qm/oracle-recovery.json'
        path.parent.mkdir(parents=True)
        value = {**run, 'scope': 'independent-jvm-oracle-jpa-crash-recovery', 'status': 'FAILED',
                 'cases': [{'name': 'OWNER_RESPONDED', 'status': 'FAILED', 'crashed_owner_reads': 1}]}
        path.write_text(json.dumps(value))
        return root, path, value

    def test_fresh_failed_measurements_are_collectable_but_not_qualified(self):
        with tempfile.TemporaryDirectory() as directory:
            root, path, value = self.fixture(directory)
            self.assertEqual(1, collect(root, 'oracle-recovery')['cases'][0]['crashed_owner_reads'])
            with self.assertRaisesRegex(ValueError, 'missing/failed'): qualify(root)

    def test_stale_wrong_run_and_malformed_fault_reports_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root, path, value = self.fixture(directory)
            os.utime(path, (time.time() - 60, time.time() - 60))
            with self.assertRaisesRegex(ValueError, 'Stale'): collect(root, 'oracle-recovery')
            path.write_text(json.dumps({**value, 'run_id': 'earlier-run'}))
            with self.assertRaisesRegex(ValueError, 'mismatch'): collect(root, 'oracle-recovery')
            path.write_text(json.dumps({**value, 'cases': [{}]}))
            with self.assertRaisesRegex(ValueError, 'Malformed'): collect(root, 'oracle-recovery')

    def test_success_label_cannot_qualify_missing_crash_and_resource_proof(self):
        with tempfile.TemporaryDirectory() as directory:
            root, path, value = self.fixture(directory)
            path.write_text(json.dumps({**value, 'status': 'PASS', 'cases': []}))
            with self.assertRaisesRegex(ValueError, 'absent'): qualify(root)
            (root / 'target/qm/run-context.json').unlink()
            with self.assertRaisesRegex(ValueError, 'marker absent'): collect(root, 'oracle-recovery')

    def test_complete_fixture_requires_physical_drain_and_thread_proof(self):
        with tempfile.TemporaryDirectory() as directory:
            root, path, value = self.fixture(directory)
            for name, (scope, required) in SCOPES.items():
                cases = [{'name': case, 'status': 'PASS', 'child_absent': True,
                          'obsolete_completion_rejected': True, 'after_crash': {}, 'after_recovery': {},
                          'recovery_ms': 1, 'owned_worker_threads_terminated': True} for case in required]
                indexed = {case['name']: case for case in cases}
                if name == 'oracle-io':
                    indexed['late-owner-and-bounded-stop'].update(
                        initial_stop={'status': 'TIMED_OUT', 'physicalOperations': 1},
                        final_stop={'status': 'DRAINED'}, successful_applications=0, client_active_after=0)
                    for key, status in [('uncertain-before-commit', 'LEASED'), ('uncertain-after-commit', 'IDLE')]:
                        indexed[key].update(projection_and_success_atomic=True, fresh_outcome='APPLIED', fresh_work_status=status)
                (path.parent / f'{name}.json').write_text(json.dumps({**value, 'scope': scope, 'status': 'PASS',
                    'cases': cases, 'owned_pool_closed': True, 'owned_executor_terminated': True}))
            self.assertEqual(2, len(qualify(root)))
            io = path.parent / 'oracle-io.json'
            current = json.loads(io.read_text())
            late = next(case for case in current['cases'] if case['name'] == 'late-owner-and-bounded-stop')
            late['initial_stop']['physicalOperations'] = 0
            io.write_text(json.dumps(current))
            with self.assertRaisesRegex(ValueError, 'physical owner/drain'): qualify(root)
            late['initial_stop']['physicalOperations'] = 1
            late['owned_worker_threads_terminated'] = False
            io.write_text(json.dumps(current))
            with self.assertRaisesRegex(ValueError, 'thread termination'): qualify(root)
