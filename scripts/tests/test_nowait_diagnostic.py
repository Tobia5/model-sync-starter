from pathlib import Path
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]


class NowaitDiagnosticTests(unittest.TestCase):
    def test_scope_selects_only_new_small_fixture(self):
        runner = (ROOT/'model-sync-sample/src/test/java/io/modelsync/sample/OracleDiagnosticRunner.java').read_text()
        self.assertIn('case "database-nowait" -> List.of("io.modelsync.sample.OracleNowaitDiagnostic");', runner)
        source = (ROOT/'model-sync-sample/src/test/java/io/modelsync/sample/OracleNowaitDiagnostic.java').read_text()
        self.assertEqual(7, source.count('@Test void '))
        self.assertNotIn('OracleCancellationDiagnostic', source)
        self.assertIn('for (int n = 0; n < 3; n++)', source)
        self.assertNotIn('DDL_LOCK_TIMEOUT', source)

    def test_cli_cannot_mix_nowait_and_cancellation(self):
        result = subprocess.run([sys.executable, str(ROOT/'scripts/full-oracle-container.py'),
                                 '--nowait-diagnostic', '--cancellation-diagnostic'], capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertIn('not allowed with argument', result.stderr)

    def test_test_configuration_requires_explicit_profile(self):
        source = (ROOT/'model-sync-sample/src/test/java/io/modelsync/sample/NowaitOracleConfiguration.java').read_text()
        self.assertIn('@Profile("nowait-oracle")', source)
        production = list(ROOT.glob('*/src/main/java/**/Nowait*.java'))
        self.assertEqual([], production)
