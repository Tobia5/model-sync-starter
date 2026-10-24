import copy
from importlib.util import module_from_spec, spec_from_file_location
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from qualification_target import manifest, validate_host, collect, qualify, characterization, audit_source, ROOT
from run_evidence import begin, invalidate


class QualificationTargetTests(unittest.TestCase):
    def receipt(self):
        value = manifest()
        return {'scope': 'named-qualification-host', 'qualification_target': value['target'],
                'claim_scope': value['claim_scope'], 'status': 'PASS', **value['policies'],
                'actual_adapter_settings_verified': True, 'qualified_mapping_consumed': True,
                'datasource_enlisted': True, 'writer_protocol': 'ledger-first',
                **{k: v for k, v in value['platform'].items() if k != 'oracle_brand'}, 'oracle': 'Oracle AI Database 26ai 23.26.3.0.0'}

    def test_default_fallback_each_policy_and_broad_qualification_are_rejected(self):
        validate_host(self.receipt())
        for key in manifest()['policies']:
            value = {**self.receipt(), key: 'DEFAULT'}
            with self.assertRaisesRegex(ValueError, 'DEFAULT'): validate_host(value)
        for key, wrong in [('claim_scope', 'all-configurations'), ('qualification_target', 'DEFAULT'),
                           ('qualified_mapping_consumed', False), ('datasource_enlisted', False), ('oracle_minor', 25)]:
            with self.assertRaises(ValueError): validate_host({**self.receipt(), key: wrong})

    def test_original_reproducer_and_exact_one_method_selection_are_preserved(self):
        audit_source()
        self.assertEqual('NOT_RUN', characterization(None)['current_status'])
        self.assertEqual('FAILED', characterization(None)['historical_capability'])
        failed = {'cases': [{'name': 'statement-lock-timeout', 'status': 'FAILED'}]}
        self.assertEqual('FAILED', characterization(failed)['current_status'])

    def fixture(self, directory):
        root = Path(directory)
        (root / '.gitignore').write_text('target/\n')
        for args in [('init',), ('config', 'user.email', 'synthetic@example.invalid'),
                     ('config', 'user.name', 'Synthetic'), ('add', '.gitignore'), ('commit', '-m', 'fixture')]:
            subprocess.run(['git', *args], cwd=root, check=True, capture_output=True)
        run = begin(root)
        folder = root / 'model-sync-sample/target/qm'
        folder.mkdir(parents=True)
        paths = []
        for role in manifest()['required_host_roles']:
            path = folder / f'qualification-host-{role}.json'
            path.write_text(json.dumps({**run, **self.receipt(), 'role': role}))
            paths.append(path)
        return root, paths

    def test_stale_wrong_run_malformed_and_blocked_following_success(self):
        with tempfile.TemporaryDirectory() as directory:
            root, paths = self.fixture(directory)
            self.assertEqual('PASS', qualify(root)['status'])
            original = paths[0].read_text()
            os.utime(paths[0], (time.time() - 60, time.time() - 60))
            with self.assertRaisesRegex(ValueError, 'Stale'): collect(root)
            paths[0].write_text(json.dumps({**json.loads(original), 'run_id': 'earlier'}))
            with self.assertRaisesRegex(ValueError, 'mismatch'): collect(root)
            paths[0].write_text('[]')
            with self.assertRaisesRegex(ValueError, 'mismatch'): collect(root)
            paths[0].write_text(original)
            paths[1].unlink()
            with self.assertRaisesRegex(ValueError, 'incomplete'): qualify(root)
            invalidate(root)
            with self.assertRaisesRegex(ValueError, 'marker absent'): collect(root)

    def test_missing_required_method_cannot_be_hidden_by_same_suite_count(self):
        spec = spec_from_file_location('named_full_gate', ROOT / 'scripts/check-full-reports.py')
        gate = module_from_spec(spec); spec.loader.exec_module(gate)
        with tempfile.TemporaryDirectory() as directory:
            root, _ = self.fixture(directory)
            for module, suites in manifest()['mandatory'].items():
                folder = root / module / 'target/failsafe-reports'; folder.mkdir(parents=True)
                for name, methods in suites.items():
                    (folder / f'TEST-{name}.xml').write_text(f'<testsuite name="{name}" tests="{len(methods)}" errors="0" failures="0" skipped="0">'
                        + ''.join(f'<testcase name="{method}"/>' for method in methods) + '</testsuite>')
            self.assertIn('OracleIoIT', gate.oracle_suites(root))
            path = root / 'model-sync-sample/target/failsafe-reports/TEST-OracleIoIT.xml'
            path.write_text(path.read_text().replace('connectionLossBeforeCommitIsUncertainUntilFreshInspection', 'substitute'))
            with self.assertRaisesRegex(ValueError, 'OracleIoIT'): gate.oracle_suites(root)
