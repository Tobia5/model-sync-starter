from pathlib import Path
import json
import os
import subprocess
import sys
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from quota_evidence import collect, qualify, SCOPE, CASES
from run_evidence import begin, invalidate


class QuotaEvidenceTests(unittest.TestCase):
    def fixture(self, directory):
        root = Path(directory)
        (root / '.gitignore').write_text('target/\n')
        subprocess.run(['git', 'init', '-q', str(root)], check=True, capture_output=True)
        subprocess.run(['git', '-c', 'user.name=Synthetic', '-c', 'user.email=synthetic@example.invalid',
                        'commit', '--allow-empty', '-qm', 'fixture'], cwd=root, check=True, capture_output=True)
        value = {**begin(root), 'scope': SCOPE, 'status': 'FAILED',
                 'cases': [{'name': 'held-acquire', 'status': 'FAILED', 'attempts': [{'measured_ms': 12}]}]}
        path = root / 'model-sync-sample/target/qm/oracle-quota-contention.json'
        path.parent.mkdir(parents=True); path.write_text(json.dumps(value))
        return root, path, value

    def test_scoped_launcher_does_not_select_old_or_expensive_suites(self):
        runner = (ROOT/'model-sync-sample/src/test/java/io/modelsync/sample/OracleDiagnosticRunner.java').read_text()
        self.assertIn('case "shared-quota" -> List.of("io.modelsync.sample.OracleQuotaContentionIT");', runner)
        source = (ROOT/'model-sync-sample/src/test/java/io/modelsync/sample/OracleQuotaContentionIT.java').read_text()
        self.assertEqual(6, source.count('@Test void '))
        for name in ('NowaitSqlProbe', 'NowaitPointJdbcTemplate', 'OracleScaleIT', 'OracleCancellationDiagnostic'):
            self.assertNotIn(name, source)
        result = subprocess.run([sys.executable, str(ROOT/'scripts/full-oracle-container.py'),
                                 '--quota-diagnostic', '--cancellation-diagnostic'], capture_output=True, text=True)
        self.assertEqual(2, result.returncode)

    def test_fresh_failed_partial_run_is_collectible_not_qualified(self):
        with tempfile.TemporaryDirectory() as directory:
            root, path, value = self.fixture(directory)
            self.assertEqual(12, collect(root)['cases'][0]['attempts'][0]['measured_ms'])
            with self.assertRaisesRegex(ValueError, 'absent or failed'): qualify(root)

    def test_stale_mismatched_malformed_missing_marker_and_blocked_run_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root, path, value = self.fixture(directory)
            os.utime(path, (time.time()-60, time.time()-60))
            with self.assertRaisesRegex(ValueError, 'Stale'): collect(root)
            path.write_text(json.dumps({**value, 'source_tree': 'earlier'}))
            with self.assertRaisesRegex(ValueError, 'mismatch'): collect(root)
            path.write_text(json.dumps({**value, 'cases': [7]}))
            with self.assertRaisesRegex(ValueError, 'Malformed'): collect(root)
            path.write_text(json.dumps({**value, 'status': 'PASS'})); invalidate(root)
            with self.assertRaisesRegex(ValueError, 'marker absent'): collect(root)

    def test_pass_label_without_server_and_rollback_proof_cannot_qualify(self):
        with tempfile.TemporaryDirectory() as directory:
            root, path, value = self.fixture(directory)
            value.update(status='PASS', cases=[{'name': name, 'status': 'PASS', 'cleanup_verified': True,
                                              'healthy_pools': True} for name in CASES],
                         owned_pools_closed=True, owned_executor_terminated=True, physical_remaining=0, table_removed=True)
            path.write_text(json.dumps(value))
            with self.assertRaisesRegex(ValueError, 'Missing quota refusal'): qualify(root)
