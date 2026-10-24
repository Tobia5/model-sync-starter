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
from lock_policy_evidence import collect, qualify, CASES, SCOPE
from run_evidence import begin, invalidate


class LockPolicyTests(unittest.TestCase):
    def fixture(self, directory):
        root = Path(directory)
        (root / '.gitignore').write_text('target/\n')
        subprocess.run(['git', 'init', '-q', str(root)], check=True, capture_output=True)
        subprocess.run(['git', '-c', 'user.name=Synthetic', '-c', 'user.email=synthetic@example.invalid',
                        'commit', '--allow-empty', '-qm', 'fixture'], cwd=root, check=True, capture_output=True)
        value = {**begin(root), 'scope': SCOPE, 'status': 'FAILED',
                 'cases': [{'name': 'producer-refusal', 'status': 'FAILED', 'measured_ms': 12}]}
        path = root / 'model-sync-sample/target/qm/oracle-lock-policy.json'
        path.parent.mkdir(parents=True); path.write_text(json.dumps(value))
        return root, path, value

    def test_production_diagnostic_selects_only_integration_and_atomicity(self):
        runner = (ROOT/'model-sync-sample/src/test/java/io/modelsync/sample/OracleDiagnosticRunner.java').read_text()
        self.assertIn('case "production-lock-policy" -> List.of("io.modelsync.sample.OracleLockPolicyIT", "io.modelsync.sample.OracleAtomicityIT");', runner)
        source = (ROOT/'model-sync-sample/src/test/java/io/modelsync/sample/OracleLockPolicyIT.java').read_text()
        self.assertEqual(7, source.count('@Test void '))
        self.assertNotIn('NowaitPointJdbcTemplate', source)
        self.assertNotIn('NowaitSqlProbe', source)
        capture = (ROOT/'model-sync-sample/src/test/java/io/modelsync/sample/LockPolicyObservation.java').read_text()
        self.assertIn('sql.add(statement); return statement;', capture)
        for module in ('model-sync-persistence-jdbc', 'model-sync-persistence-jpa'):
            for path in (ROOT/module/'src/main/java').rglob('*.java'):
                self.assertNotIn('StatementInspector', path.read_text())

    def test_new_mode_cannot_mix_with_old_prototype(self):
        result = subprocess.run([sys.executable, str(ROOT/'scripts/full-oracle-container.py'),
                                 '--lock-policy-diagnostic', '--nowait-diagnostic'], capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertIn('not allowed with argument', result.stderr)

    def test_fresh_failing_run_is_collected_without_qualification(self):
        with tempfile.TemporaryDirectory() as directory:
            root, path, value = self.fixture(directory)
            self.assertEqual(12, collect(root)['cases'][0]['measured_ms'])
            with self.assertRaisesRegex(ValueError, 'absent or failed'): qualify(root)

    def test_stale_mismatched_malformed_and_blocked_after_success_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root, path, value = self.fixture(directory)
            os.utime(path, (time.time()-60, time.time()-60))
            with self.assertRaisesRegex(ValueError, 'Stale'): collect(root)
            path.write_text(json.dumps({**value, 'run_id': 'earlier'}))
            with self.assertRaisesRegex(ValueError, 'mismatch'): collect(root)
            path.write_text(json.dumps({**value, 'cases': [7]}))
            with self.assertRaisesRegex(ValueError, 'Malformed'): collect(root)
            path.write_text(json.dumps({**value, 'status': 'PASS'}))
            invalidate(root)  # A new blocked preflight cannot borrow earlier successful labels.
            with self.assertRaisesRegex(ValueError, 'marker absent'): collect(root)

    def test_pass_label_cannot_replace_required_refusal_or_physical_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            root, path, value = self.fixture(directory)
            value.update(status='PASS', cases=[{'name': name, 'status': 'PASS', 'cleanup_verified': True} for name in CASES])
            path.write_text(json.dumps(value))
            with self.assertRaisesRegex(ValueError, 'cleanup'): qualify(root)
            value.update(owned_pools_closed=True, owned_executor_terminated=True, physical_remaining=0, table_removed=True)
            path.write_text(json.dumps(value))
            with self.assertRaisesRegex(ValueError, 'Malformed database refusal'): qualify(root)
