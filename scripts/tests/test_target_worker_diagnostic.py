from pathlib import Path
import subprocess
import sys
import unittest

ROOT=Path(__file__).resolve().parents[2]

class TargetWorkerDiagnosticTests(unittest.TestCase):
    def test_explicit_small_scope_and_independent_cells(self):
        runner=(ROOT/'model-sync-sample/src/test/java/io/modelsync/sample/OracleDiagnosticRunner.java').read_text()
        self.assertIn('case "target-and-worker" -> List.of("io.modelsync.sample.OracleQualificationTargetIT", "io.modelsync.sample.OracleWorkerDiagnostic");',runner)
        source=(ROOT/'model-sync-sample/src/test/java/io/modelsync/sample/OracleWorkerDiagnostic.java').read_text()
        self.assertEqual(4,source.count('@Test void '))
        self.assertNotIn('OracleOptimizationIT',source)
        self.assertNotIn('OracleCapacityIT',source)
        self.assertIn('report.put("condition_ms",20000)',source)
        self.assertIn('report.put("measured_completions",160)',source)

    def test_cli_refuses_combined_scopes(self):
        run=subprocess.run([sys.executable,str(ROOT/'scripts/full-oracle-container.py'),
                            '--target-worker-diagnostic','--diagnostic'],capture_output=True,text=True)
        self.assertEqual(2,run.returncode)
        self.assertIn('not allowed with argument',run.stderr)
