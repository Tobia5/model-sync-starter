"""Negative checks for full preflight and report evidence admission."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile
import unittest
import json
import time
from importlib.util import module_from_spec, spec_from_file_location

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from run_evidence import begin, current, fresh, invalidate

ROOT = Path(__file__).resolve().parents[2]


class QualityGateTests(unittest.TestCase):
    def fixture_repo(self, directory):
        root = Path(directory)
        subprocess.run(['git', 'init', '-q', str(root)], check=True, capture_output=True)
        (root / 'tracked.txt').write_text('source', encoding='utf-8')
        (root / '.gitignore').write_text('target/\n', encoding='utf-8')
        subprocess.run(['git', '-C', str(root), 'add', 'tracked.txt', '.gitignore'], check=True, capture_output=True)
        subprocess.run(['git', '-C', str(root), '-c', 'user.name=Fixture', '-c',
                        'user.email=fixture@example.invalid', 'commit', '-qm', 'fixture'], check=True)
        return root

    def test_run_evidence_rejects_missing_marker_and_malformed_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            report = root / 'report.xml'
            report.write_text('<testsuite tests="1"/>', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'marker absent'):
                fresh(report, root)
            begin(root)
            marker = root / 'target/qm/run-context.json'
            marker.write_text('{bad json', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Malformed'):
                current(root)

    def test_run_evidence_rejects_stale_report_and_changed_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            report = root / 'report.xml'
            report.write_text('<testsuite tests="1"/>', encoding='utf-8')
            old = time.time() - 60
            os.utime(report, (old, old))
            begin(root)
            with self.assertRaisesRegex(ValueError, 'Stale'):
                fresh(report, root)
            report.write_text('<testsuite tests="1"/>', encoding='utf-8')
            fresh(report, root)
            (root / 'tracked.txt').write_text('changed', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'source identity mismatch'):
                current(root)

    def test_blocked_run_after_success_does_not_inherit_reports(self):
        spec = spec_from_file_location('qm_report', ROOT / 'scripts/qm-report.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            (root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"/>', encoding='utf-8')
            begin(root)
            pit = root / 'model-sync-testkit/target/pit-reports/mutations.xml'
            pit.parent.mkdir(parents=True)
            pit.write_text('<mutations><mutation status="KILLED"/></mutations>', encoding='utf-8')
            self.assertEqual('FAILED', module.collect(True, root)['mutation']['status'])
            invalidate(root)
            result = module.collect(True, root)
            self.assertEqual('NONE', result['attribution'])
            self.assertEqual('NOT_RUN', result['mutation']['status'])
            self.assertEqual({}, result['modules'])

    def test_failed_pit_run_retains_fresh_measured_counts(self):
        spec = spec_from_file_location('qm_report_pit', ROOT / 'scripts/qm-report.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            (root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"/>', encoding='utf-8')
            run = begin(root)
            pit = root / 'model-sync-testkit/target/pit-reports/mutations.xml'
            pit.parent.mkdir(parents=True)
            pit.write_text('<mutations>' + '<mutation status="KILLED"/>' +
                           '<mutation status="SURVIVED"/>' * 3 + '</mutations>', encoding='utf-8')
            (pit.parent / 'index.html').write_text('<html/>', encoding='utf-8')
            result = module.collect(True, root)
            self.assertEqual(run['run_id'], result['attribution'])
            self.assertEqual('FAILED', result['mutation']['status'])
            self.assertEqual({'KILLED': 1, 'SURVIVED': 3}, result['mutation']['counts'])
            self.assertEqual(25.0, result['mutation']['killed_over_generated_percent'])

    def test_failed_oracle_suite_retains_fresh_measured_failure(self):
        spec = spec_from_file_location('qm_report_oracle', ROOT / 'scripts/qm-report.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            (root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><modules><module>model-sync-persistence-jdbc</module></modules></project>', encoding='utf-8')
            begin(root)
            report = root / 'model-sync-persistence-jdbc/target/failsafe-reports/TEST-io.modelsync.OracleSmokeIT.xml'
            report.parent.mkdir(parents=True)
            report.write_text('<testsuite name="io.modelsync.OracleSmokeIT" tests="2" errors="0" failures="1" skipped="0"/>', encoding='utf-8')
            result = module.collect(True, root)
            suite = result['modules']['model-sync-persistence-jdbc']['failsafe']
            self.assertEqual('FAILED', suite['status'])
            self.assertEqual(2, suite['tests'])
            self.assertEqual(1, suite['failures'])

    def test_collector_rejects_stale_and_malformed_reports(self):
        spec = spec_from_file_location('qm_report_reject', ROOT / 'scripts/qm-report.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            (root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><modules><module>model-sync-core</module></modules></project>', encoding='utf-8')
            stale = root / 'model-sync-core/target/surefire-reports/TEST-Stale.xml'
            stale.parent.mkdir(parents=True)
            stale.write_text('<testsuite tests="5" failures="0" errors="0" skipped="0"/>', encoding='utf-8')
            old = time.time() - 60
            os.utime(stale, (old, old))
            begin(root)
            malformed = stale.parent / 'TEST-Malformed.xml'
            malformed.write_text('<testsuite', encoding='utf-8')
            suite = module.collect(False, root)['modules']['model-sync-core']['surefire']
            self.assertEqual('REJECTED', suite['status'])
            self.assertEqual(0, suite['files'])
            self.assertEqual(0, suite.get('tests', 0))
            self.assertEqual(2, len(suite['rejected']))

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

    def test_container_lane_requires_explicit_opt_in(self):
        env = dict(os.environ)
        env.pop('MODELSYNC_ORACLE_CONTAINER', None)
        result = subprocess.run([sys.executable, 'scripts/full-oracle-container.py'],
                                cwd=ROOT, env=env, capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertIn('EXPLICIT_DISPOSABLE', result.stderr)

    def test_full_report_rejects_missing_raw_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            script = ROOT / 'scripts/check-full-reports.py'
            spec = __import__('importlib.util', fromlist=['spec_from_file_location']).spec_from_file_location('full_gate', script)
            module = __import__('importlib.util', fromlist=['module_from_spec']).module_from_spec(spec)
            spec.loader.exec_module(module)
            with self.assertRaises(ValueError):
                module.check(Path(directory))

    def test_full_report_rejects_malformed_pit_xml_for_current_run(self):
        spec = spec_from_file_location('full_gate_pit', ROOT / 'scripts/check-full-reports.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            begin(root)
            pit = root / 'model-sync-testkit/target/pit-reports/mutations.xml'
            pit.parent.mkdir(parents=True)
            pit.write_text('<invalid', encoding='utf-8')
            with self.assertRaises(Exception):
                module.pit_counts(root)

    def test_profile_audit_separates_default_and_full(self):
        spec = __import__('importlib.util', fromlist=['spec_from_file_location']).spec_from_file_location('profiles', ROOT / 'scripts/check-profiles.py')
        module = __import__('importlib.util', fromlist=['module_from_spec']).module_from_spec(spec)
        spec.loader.exec_module(module)
        base, full = module.check(ROOT)
        self.assertNotIn('pitest-maven', [plugin for plugin, _ in base])
        self.assertIn('pitest-maven', [plugin for plugin, _ in full])


if __name__ == '__main__':
    unittest.main()
