import importlib.util
import json
import os
from pathlib import Path
import tempfile
import time
import unittest

SPEC = importlib.util.spec_from_file_location('consumer_artifacts', Path(__file__).resolve().parents[1] / 'consumer_artifacts.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class IndependentConsumerGuards(unittest.TestCase):
    def test_resource_budget_is_not_lowered(self):
        value = {'samples': [{'host_available_gib': 12}] * 3,
                 'docker': {'available_gib': 6, 'cpu': 4}, 'disk_free_gib': [20, 20]}
        self.assertTrue(MODULE.admitted(value))
        for field in ('host', 'docker', 'disk'):
            changed = json.loads(json.dumps(value))
            if field == 'host': changed['samples'][1]['host_available_gib'] = 11.99
            if field == 'docker': changed['docker']['available_gib'] = 5.99
            if field == 'disk': changed['disk_free_gib'][0] = 19.99
            self.assertFalse(MODULE.admitted(changed))

    def test_workspace_must_be_outside_reactor(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); inside = root / 'consumer'; inside.mkdir()
            with self.assertRaises(ValueError): MODULE.outside(root, inside)

    def test_missing_attachment_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            for artifact, suffixes in [('model-sync-parent', ['.pom']), ('model-sync-core', ['.pom', '.jar', '-sources.jar'])]:
                folder = repository / 'io/modelsync' / artifact / MODULE.VERSION; folder.mkdir(parents=True)
                for suffix in suffixes: (folder / (artifact + '-' + MODULE.VERSION + suffix)).write_bytes(b'synthetic')
            with self.assertRaisesRegex(ValueError, 'attachment'): MODULE.artifact_files(repository, ('core',))

    def test_changed_artifact_hash_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); (root / 'staged').mkdir(); (root / 'staged/library.jar').write_bytes(b'wrong')
            with self.assertRaisesRegex(ValueError, 'hash'):
                MODULE.verify_hashes(root, {'artifacts': [{'path': 'library.jar', 'sha256': 'expected'}]})

    def test_demo_or_absent_dependencies_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'tree.json'
            for tree in ({}, {'groupId': 'io.modelsync', 'artifactId': 'model-sync-sample'}):
                path.write_text(json.dumps(tree))
                with self.assertRaises(ValueError): MODULE.validate_tree(path)

    def test_stale_and_malformed_receipts_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'receipt.json'; started = time.time_ns()
            for content in ('', '{'):
                path.write_text(content)
                with self.assertRaises(ValueError): MODULE.check_receipt(path, 'run', {}, 'mvc', started, 'manifest')
            os.utime(path, ns=(1, 1))
            with self.assertRaisesRegex(ValueError, 'Stale'): MODULE.check_receipt(path, 'run', {}, 'mvc', started, 'manifest')

    def test_source_run_policy_and_physical_drain_are_required(self):
        manifest = {'producer': {'source_sha': 'sha', 'source_tree': 'tree'}}
        value = {'status': 'PASS', 'run_id': 'run', 'producer_source': 'sha', 'producer_tree': 'tree',
                 'consumer_manifest_sha256': 'manifest', 'framework_mode': 'mvc', 'datasource_enlisted': True,
                 'target': 'oracle26-nowait-ledger-first-v1', 'committed_reconciliations': 3, 'owner_calls': 3,
                 'disabled_mode': 'PASS', 'pool_closed': True, 'disabled_pool_closed': True,
                 'fixture_rows_remaining': 0, 'owned_scheduler_threads_after_close': 0,
                 'effective_policies': {'point': 'ORACLE_NOWAIT', 'quota': 'ORACLE_NOWAIT', 'insert': 'ORACLE_26_NOWAIT',
                                        'jpa': 'ORACLE_NOWAIT', 'cooldown': 'DURABLE', 'extension': 1},
                 'shutdown': {'status': 'DRAINED', 'physical': 0, 'owner': 0}}
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'receipt.json'; path.write_text(json.dumps(value))
            self.assertEqual('PASS', MODULE.check_receipt(path, 'run', manifest, 'mvc', 1, 'manifest')['status'])
            for key, replacement in [('run_id', 'old'), ('producer_source', 'old'), ('consumer_manifest_sha256', 'old'),
                                     ('effective_policies', {}), ('shutdown', {'status': 'DRAINED', 'physical': 1, 'owner': 0}),
                                     ('committed_reconciliations', 0), ('pool_closed', False), ('status', 'FAILED')]:
                changed = dict(value, **{key: replacement}); path.write_text(json.dumps(changed))
                with self.assertRaises(ValueError): MODULE.check_receipt(path, 'run', manifest, 'mvc', 1, 'manifest')


if __name__ == '__main__': unittest.main()
