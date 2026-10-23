"""Cheap guards for the explicitly selected, small JDBC diagnostic lane."""
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import os
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]


class CancellationDiagnosticTests(unittest.TestCase):
    def module(self):
        spec = spec_from_file_location('cancellation_container', ROOT / 'scripts/full-oracle-container.py')
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_diagnostic_preflight_cannot_execute_full_verify(self):
        module = self.module()
        with patch.dict(os.environ, {'MODELSYNC_MAVEN_SETTINGS': module.PROJECT_SETTINGS}):
            command = module.maven_command(True)
        self.assertEqual(['-Pfull', '-N', 'initialize'], command[-3:])
        self.assertNotIn('verify', command)
        self.assertNotIn('skipTests', ' '.join(command))

    def test_cancellation_selects_only_small_fixture_and_is_not_a_default_suite(self):
        runner = (ROOT / 'model-sync-sample/src/test/java/io/modelsync/sample/OracleDiagnosticRunner.java').read_text()
        self.assertIn('case "jdbc-cancellation" -> List.of("io.modelsync.sample.OracleCancellationDiagnostic");', runner)
        source = (ROOT / 'model-sync-sample/src/test/java/io/modelsync/sample/OracleCancellationDiagnostic.java').read_text()
        self.assertEqual(6, source.count('@Test void '))
        self.assertIn('"full_qualification", "NOT_RUN"', source)
        self.assertIn('NOT_APPLICABLE_CONTAINMENT_ONLY', source)
        self.assertNotIn('.cancel(true)', source)
        self.assertNotIn('OracleCapacityIT', source)
        self.assertIn('blocking_session=?', source)
        self.assertIn('"blocker_release_credited_as_cancellation", false', source)
        container = (ROOT / 'scripts/full-oracle-container.py').read_text()
        self.assertIn("['-Xmx512m', '-XX:MaxDirectMemorySize=128m'] if cancellation else []", container)

    def test_cli_diagnostic_selection_is_exclusive(self):
        import subprocess
        result = subprocess.run([os.sys.executable, str(ROOT / 'scripts/full-oracle-container.py'),
                                 '--cancellation-diagnostic', '--recovery-diagnostic'], capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertIn('not allowed with argument', result.stderr)


if __name__ == '__main__':
    unittest.main()
