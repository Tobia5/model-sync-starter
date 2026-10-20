"""Negative checks for full preflight and report evidence admission."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class QualityGateTests(unittest.TestCase):
    def disposable_inputs(self):
        env = dict(os.environ)
        for key in ('MODELSYNC_ORACLE_URL', 'MODELSYNC_ORACLE_PASSWORD',
                    'MODELSYNC_ORACLE_MIGRATION_URL', 'MODELSYNC_ORACLE_MIGRATION_PASSWORD'):
            env[key] = 'synthetic'
        env['MODELSYNC_ORACLE_USER'] = 'MS_TEST_RUNTIME'
        env['MODELSYNC_ORACLE_MIGRATION_USER'] = 'MS_TEST_MIGRATION'
        env['MODELSYNC_ORACLE_TEST_SCHEMA_ACK'] = 'DEDICATED_TEST_SCHEMA'
        env['MODELSYNC_ORACLE_MIGRATION_SCHEMA_ACK'] = 'DEDICATED_MIGRATION_SCHEMA'
        env['MODELSYNC_FULL_ENV_ACK'] = 'DISPOSABLE_NONPRODUCTION'
        env.pop('MODELSYNC_LOAD_TARGET', None)
        return env

    def test_full_preflight_rejects_skip_even_with_disposable_inputs(self):
        env = self.disposable_inputs()
        result = subprocess.run([sys.executable, 'scripts/full-preflight.py', 'skipTests=true'],
                                cwd=ROOT, env=env, capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertIn('skipTests', result.stderr)

    def test_full_preflight_accepts_declared_unfiltered_inputs(self):
        result = subprocess.run([sys.executable, 'scripts/full-preflight.py'], cwd=ROOT,
                                env=self.disposable_inputs(), capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_full_preflight_rejects_unapproved_remote_target(self):
        env = dict(os.environ, MODELSYNC_LOAD_TARGET='http://example.com')
        result = subprocess.run([sys.executable, 'scripts/full-preflight.py'],
                                cwd=ROOT, env=env, capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertIn('Remote load target', result.stderr)

    def test_full_report_rejects_missing_raw_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            script = ROOT / 'scripts/check-full-reports.py'
            spec = __import__('importlib.util', fromlist=['spec_from_file_location']).spec_from_file_location('full_gate', script)
            module = __import__('importlib.util', fromlist=['module_from_spec']).module_from_spec(spec)
            spec.loader.exec_module(module)
            with self.assertRaises(ValueError):
                module.check(Path(directory))

    def test_profile_audit_separates_default_and_full(self):
        spec = __import__('importlib.util', fromlist=['spec_from_file_location']).spec_from_file_location('profiles', ROOT / 'scripts/check-profiles.py')
        module = __import__('importlib.util', fromlist=['module_from_spec']).module_from_spec(spec)
        spec.loader.exec_module(module)
        base, full = module.check(ROOT)
        self.assertNotIn('pitest-maven', [plugin for plugin, _ in base])
        self.assertIn('pitest-maven', [plugin for plugin, _ in full])


if __name__ == '__main__':
    unittest.main()
