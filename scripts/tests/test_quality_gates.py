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
from unittest.mock import patch

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

    def test_run_identity_rejects_changed_contents_of_an_already_dirty_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            source = root / 'tracked.txt'
            source.write_text('first dirty contents', encoding='utf-8')
            begin(root)
            source.write_text('different dirty contents', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'source identity mismatch'):
                current(root)

    def test_run_identity_rejects_changed_untracked_contents(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            source = root / 'new-source.txt'
            source.write_text('first contents', encoding='utf-8')
            begin(root)
            source.write_text('different contents', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'source identity mismatch'):
                current(root)

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

    def gatling_fixture(self, root):
        (root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"/>')
        begin(root)
        report = root / 'model-sync-performance/target/gatling/freshvisitssimulation-current'
        (report / 'js').mkdir(parents=True)
        (report / 'index.html').write_text('<html/>')
        (report / 'simulation.log').write_bytes(b'raw Gatling fixture')
        stats = {'stats': {'numberOfRequests': {'total': 40, 'ok': 17, 'ko': 23},
                           'percentiles1': {'total': 10}, 'percentiles3': {'total': 20},
                           'percentiles4': {'total': 30}}}
        (report / 'js/stats.json').write_text(json.dumps(stats))
        (report / 'js/assertions.xml').write_text('<testsuite tests="1" failures="1" errors="0"/>')
        harness = root / 'model-sync-performance/target/qm/load-fresh.json'
        harness.parent.mkdir(parents=True)
        harness.write_text('{"scenario":"fresh","status":"PASS"}')
        return report

    def test_oracle_atomicity_is_required_and_partial_failures_are_collected(self):
        gate_spec = spec_from_file_location('atomicity_gate', ROOT / 'scripts/check-full-reports.py')
        gate = module_from_spec(gate_spec)
        gate_spec.loader.exec_module(gate)
        qm_spec = spec_from_file_location('atomicity_qm', ROOT / 'scripts/qm-report.py')
        qm = module_from_spec(qm_spec)
        qm_spec.loader.exec_module(qm)
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            (root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><modules>'
                '<module>model-sync-persistence-jdbc</module><module>model-sync-sample</module></modules></project>')
            begin(root)
            for name in gate.ORACLE:
                report = root / 'model-sync-persistence-jdbc/target/failsafe-reports' / f'TEST-{name}.xml'
                report.parent.mkdir(parents=True, exist_ok=True)
                report.write_text(f'<testsuite name="{name}" tests="1" failures="0" errors="0" skipped="0"/>')
            with self.assertRaisesRegex(ValueError, 'OracleAtomicityIT'):
                gate.oracle_suites(root)
            report = root / 'model-sync-sample/target/failsafe-reports/TEST-OracleAtomicityIT.xml'
            report.parent.mkdir(parents=True)
            report.write_text('<testsuite name="OracleAtomicityIT" tests="7" failures="1" errors="0" skipped="0"/>')
            with self.assertRaisesRegex(ValueError, 'OracleAtomicityIT'):
                gate.oracle_suites(root)
            collected = qm.collect(True, root)['modules']['model-sync-sample']['failsafe']
            self.assertEqual('FAILED', collected['status'])
            self.assertEqual(7, collected['tests'])
            self.assertEqual(1, collected['failures'])
            report.write_text('<testsuite name="OracleAtomicityIT" tests="7" failures="0" errors="0" skipped="0"/>')
            self.assertIn('OracleAtomicityIT', gate.oracle_suites(root))

    def test_failed_gatling_retains_http_counts_despite_passing_harness(self):
        spec = spec_from_file_location('qm_report_gatling', ROOT / 'scripts/qm-report.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            self.gatling_fixture(root)
            result = module.collect(True, root)['gatling']
            self.assertEqual('FAILED', result['fresh']['status'])
            self.assertEqual('PASS', result['fresh']['harness_status'])
            self.assertEqual({'total': 40, 'ok': 17, 'ko': 23}, result['fresh']['http']['requests'])
            self.assertEqual(1, result['fresh']['http']['assertions']['failures'])
            self.assertEqual('NOT_RUN', result['hot']['status'])

    def test_gatling_rejects_stale_assertions_and_malformed_statistics(self):
        from gatling_evidence import measured
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            report = self.gatling_fixture(root)
            assertions = report / 'js/assertions.xml'
            old = time.time() - 60
            os.utime(assertions, (old, old))
            with self.assertRaisesRegex(ValueError, 'Stale'):
                measured(root, 'fresh')
            assertions.write_text('<testsuite tests="1" failures="0" errors="0"/>')
            (report / 'js/stats.json').write_text('{"stats":{}}')
            with self.assertRaisesRegex(ValueError, 'Malformed'):
                measured(root, 'fresh')

    def test_gatling_http_failures_fail_even_when_assertion_xml_passes(self):
        from gatling_evidence import measured
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            report = self.gatling_fixture(root)
            (report / 'js/assertions.xml').write_text('<testsuite tests="1" failures="0" errors="0"/>')
            self.assertEqual('FAILED', measured(root, 'fresh')['status'])
            stats = json.loads((report / 'js/stats.json').read_text())
            stats['stats']['numberOfRequests'] = {'total': 40, 'ok': 40, 'ko': 0}
            (report / 'js/stats.json').write_text(json.dumps(stats))
            self.assertEqual('PASS', measured(root, 'fresh')['status'])

    def test_executed_pit_and_oracle_without_reports_are_failed(self):
        spec = spec_from_file_location('qm_report_no_xml', ROOT / 'scripts/qm-report.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            (root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><modules><module>model-sync-persistence-jdbc</module></modules></project>', encoding='utf-8')
            begin(root)
            log = root / 'target/qm/full-build.log'
            log.write_text('[INFO] --- pitest:1.25.9:mutationCoverage (behavioral-mutations) @ model-sync-testkit ---\n'
                           '[INFO] Running io.modelsync.jdbc.OracleSmokeIT\n', encoding='utf-8')
            result = module.collect(True, root)
            self.assertEqual('FAILED', result['mutation']['status'])
            suite = result['modules']['model-sync-persistence-jdbc']['failsafe']
            self.assertEqual('FAILED', suite['status'])
            self.assertEqual('OracleSmokeIT', suite['suites'][0]['name'])
            self.assertEqual(0, suite['suites'][0]['tests'])

    def test_utf16_failed_build_log_keeps_executed_full_goals_failed(self):
        spec = spec_from_file_location('qm_report_utf16', ROOT / 'scripts/qm-report.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            (root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><modules><module>model-sync-persistence-jdbc</module></modules></project>', encoding='utf-8')
            begin(root)
            (root / 'target/qm/full-build.log').write_text(
                '[INFO] --- pitest:1.25.9:mutationCoverage (behavioral-mutations) @ model-sync-testkit ---\n'
                '[INFO] Running io.modelsync.jdbc.OracleScaleIT\n'
                '[INFO] --- gatling:4.11.0:test (fresh-visits) @ model-sync-performance ---\n', encoding='utf-16')
            result = module.collect(True, root)
            self.assertEqual('FAILED', result['mutation']['status'])
            self.assertEqual('FAILED', result['oracle_scale']['status'])
            self.assertEqual('FAILED', result['gatling']['fresh']['status'])

    def test_failed_oracle_xml_proves_execution_without_a_build_log(self):
        spec = spec_from_file_location('qm_report_xml_execution', ROOT / 'scripts/qm-report.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            (root / 'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><modules><module>model-sync-persistence-jdbc</module></modules></project>', encoding='utf-8')
            begin(root)
            report = root / 'model-sync-persistence-jdbc/target/failsafe-reports/TEST-OracleScaleIT.xml'
            report.parent.mkdir(parents=True)
            report.write_text('<testsuite name="io.modelsync.jdbc.OracleScaleIT" tests="1" errors="1" failures="0" skipped="0"/>', encoding='utf-8')
            result = module.collect(True, root)
            self.assertEqual('FAILED', result['oracle_scale']['status'])

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
        spec = spec_from_file_location('oracle_container_preflight', ROOT / 'scripts/full-oracle-container.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        with patch.dict(os.environ, {'MODELSYNC_ORACLE_CONTAINER': ''}):
            with self.assertRaisesRegex(ValueError, 'EXPLICIT_DISPOSABLE'):
                module.preflight()

    def test_queue_evidence_rejects_estimated_plans_and_combined_or_regressed_counters(self):
        from queue_evidence import plans, counters
        cursor = {'SQL_ID': 'fixture', 'EXECUTIONS': 1, 'CHILD_ADDRESS': 'A0',
                  'LAST_LOAD_TIME': '2026-10-20/00:00:00', 'LOADS': 1, 'window_executions': 1}
        with self.assertRaisesRegex(ValueError, 'actual row-source'):
            plans([{**cursor, 'actual_plan': ['EXPLAIN PLAN estimates only']}])
        with self.assertRaisesRegex(ValueError, 'Separate Oracle counters'):
            counters({'db_session_work': 100})
        with self.assertRaisesRegex(ValueError, 'regressed'):
            counters({'db block gets': 1, 'consistent gets': -1, 'physical reads': 0,
                      'execute count': 2, 'redo size': 1})
        plans([{**cursor, 'actual_plan': ['| Starts | A-Rows | Buffers |']}])
        with self.assertRaisesRegex(ValueError, 'loaded-cursor'):
            plans([{**cursor, 'window_executions': 0, 'actual_plan': ['| A-Rows |']}])

    def test_container_uses_selected_project_settings_and_keeps_full_goals(self):
        spec = spec_from_file_location('oracle_container_maven', ROOT / 'scripts/full-oracle-container.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        with patch.dict(os.environ, {'MODELSYNC_MAVEN_SETTINGS': module.PROJECT_SETTINGS}):
            command = module.maven_command()
        self.assertEqual(['-Pfull', 'clean', 'verify'], command[-3:])
        self.assertEqual(str(ROOT / module.PROJECT_SETTINGS), command[command.index('-s') + 1])
        with patch.dict(os.environ, {'MODELSYNC_MAVEN_SETTINGS': '../unreviewed.xml'}):
            with self.assertRaisesRegex(ValueError, 'reviewed project Maven settings'):
                module.maven_command()

    def test_diagnostics_select_full_preflight_but_cannot_substitute_full_verify(self):
        spec = spec_from_file_location('oracle_diagnostic_prepare', ROOT / 'scripts/full-oracle-container.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        with patch.dict(os.environ, {'MODELSYNC_MAVEN_SETTINGS': module.PROJECT_SETTINGS}):
            self.assertEqual(['-Pfull', 'clean', 'verify'], module.maven_command()[-3:])
            self.assertEqual(['-Pfull', '-N', 'initialize'], module.maven_command(True)[-3:])
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            with self.assertRaisesRegex(ValueError, 'marker absent'):
                module.diagnostic_command(root)

    def test_diagnostic_launcher_requires_fresh_default_reports_and_compiled_output(self):
        spec = spec_from_file_location('oracle_diagnostic_command', ROOT / 'scripts/full-oracle-container.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture_repo(directory)
            begin(root)
            log = root / 'target/qm/default-build.log'
            log.write_text('[INFO] BUILD SUCCESS')
            report = root / 'model-sync-sample/target/surefire-reports/TEST-io.modelsync.sample.JpaCompletionAtomicityTest.xml'
            report.parent.mkdir(parents=True)
            report.write_text('<testsuite><properties><property name="java.class.path" value="missing-launcher"/></properties></testsuite>')
            success = subprocess.CompletedProcess([], 0, stdout='default guard PASS')
            original_run = subprocess.run
            guard_calls = []
            def run_guard(command, *args, **kwargs):
                if command[0] == sys.executable:
                    guard_calls.append(command)
                    return success
                return original_run(command, *args, **kwargs)
            with patch.object(module.subprocess, 'run', side_effect=run_guard):
                with self.assertRaisesRegex(ValueError, 'launcher classpath absent'):
                    module.diagnostic_command(root)
            report.write_text('<testsuite><properties><property name="java.class.path" value="junit-platform-launcher-6.0.3.jar"/></properties></testsuite>')
            for name in ('model-sync-sample/target/test-classes/io/modelsync/sample/OracleDiagnosticRunner.class',
                         'model-sync-persistence-jdbc/target/test-classes/io/modelsync/jdbc/OracleScaleIT.class'):
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b'fixture')
            java = root / 'target/jdk/bin' / ('java.exe' if os.name == 'nt' else 'java')
            java.parent.mkdir(parents=True)
            java.write_bytes(b'fixture')
            guard_calls.clear()
            with patch.dict(os.environ, {'JAVA_HOME': str(root / 'target/jdk')}), patch.object(module.subprocess, 'run', side_effect=run_guard):
                command = module.diagnostic_command(root)
                self.assertEqual('io.modelsync.sample.OracleDiagnosticRunner', command[-1])
                self.assertIn(str(root / 'model-sync-persistence-jdbc/target/test-classes'), command[-2])
                self.assertEqual(2, len(guard_calls))
                log.write_text('[INFO] BUILD FAILURE')
                with self.assertRaisesRegex(ValueError, 'successful.*default build log'):
                    module.diagnostic_command(root)
                log.write_text('[INFO] BUILD SUCCESS')
                old = time.time() - 60
                os.utime(report, (old, old))
                with self.assertRaisesRegex(ValueError, 'Stale'):
                    module.diagnostic_command(root)

    def test_container_preflight_uses_supported_docker_info_field(self):
        spec = spec_from_file_location('oracle_container_docker_info', ROOT / 'scripts/full-oracle-container.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        env = {'MODELSYNC_ORACLE_CONTAINER': 'EXPLICIT_DISPOSABLE',
               'MODELSYNC_FULL_ENV_ACK': 'DISPOSABLE_NONPRODUCTION',
               'MODELSYNC_ORACLE_URL': '', 'MODELSYNC_ORACLE_USER': '', 'MODELSYNC_ORACLE_PASSWORD': ''}
        with patch.dict(os.environ, env), patch.object(module, 'docker') as command:
            module.preflight()
        command.assert_called_once_with('info', '--format', '{{.ServerVersion}}')

    def test_schema_setup_streams_sql_without_password_arguments_and_redacts_errors(self):
        spec = spec_from_file_location('oracle_container_sql', ROOT / 'scripts/full-oracle-container.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        password = 'generated-fixture-password'
        script = f'create user MS_TEST_RUNTIME identified by "{password}";\n'
        failure = subprocess.CompletedProcess([], 1, stdout=script, stderr='setup failed')
        with patch.object(module.subprocess, 'run', return_value=failure) as command:
            with self.assertRaises(RuntimeError) as raised:
                module.apply_schema_script('msq-fixture', script, (password,))
        self.assertNotIn(password, str(raised.exception))
        self.assertIn('[REDACTED]', str(raised.exception))
        self.assertNotIn(password, ' '.join(command.call_args.args[0]))
        self.assertIn(script, command.call_args.kwargs['input'])

    def test_cleanup_accepts_docker29_absence_but_rejects_daemon_errors(self):
        spec = spec_from_file_location('oracle_container_cleanup', ROOT / 'scripts/full-oracle-container.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        self.assertTrue(module.container_absent(subprocess.CompletedProcess([], 1, stderr='error: no such object: msq-fixture')))
        self.assertFalse(module.container_absent(subprocess.CompletedProcess([], 1, stderr='daemon unavailable')))

    def test_session_volume_refuses_existing_storage(self):
        spec = spec_from_file_location('oracle_volume_existing', ROOT / 'scripts/full-oracle-container.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        with patch.object(module, 'docker', return_value=subprocess.CompletedProcess([], 0, stdout='existing')) as command:
            with self.assertRaisesRegex(RuntimeError, 'refusing to reuse'):
                module.create_session_volume('msq-fixture-data', 'attempt-fixture')
        self.assertEqual(1, command.call_count)

    def test_session_volume_cleanup_refuses_another_owner(self):
        spec = spec_from_file_location('oracle_volume_owner', ROOT / 'scripts/full-oracle-container.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        other = subprocess.CompletedProcess([], 0, stdout='{"io.modelsync.qualification.attempt":"another-attempt"}')
        with patch.object(module, 'docker', return_value=other) as command:
            self.assertFalse(module.remove_session_volume('msq-fixture-data', 'attempt-fixture'))
        self.assertEqual(1, command.call_count)

    def test_session_volume_cleanup_verifies_removal(self):
        spec = spec_from_file_location('oracle_volume_cleanup', ROOT / 'scripts/full-oracle-container.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        results = [subprocess.CompletedProcess([], 0, stdout='{"io.modelsync.qualification.attempt":"attempt-fixture"}'),
                   subprocess.CompletedProcess([], 0, stdout='msq-fixture-data'),
                   subprocess.CompletedProcess([], 1, stderr='error: no such volume: msq-fixture-data')]
        with patch.object(module, 'docker', side_effect=results):
            self.assertTrue(module.remove_session_volume('msq-fixture-data', 'attempt-fixture'))

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
