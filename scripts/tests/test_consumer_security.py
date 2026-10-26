import copy
import importlib.util
from pathlib import Path
import sys
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
import consumer_security as security


class ConsumerSecurityEvidenceGuards(unittest.TestCase):
    def receipt(self):
        return {'run_id': 'current', 'fixture_source': 'source', 'fixture_tree': 'tree',
                'artifact_source': security.ARTIFACT_SOURCE, 'manifest_sha256': 'hash', 'mode': 'webflux',
                'status': 'PASS', 'owner_calls': 4, 'committed_reconciliations': 4,
                'negative_cases': [{'case': name, 'side_effects_unchanged': True} for name in security.NEGATIVE_CASES],
                'pool_closed': True, 'disabled_mode': True, 'artifact_resolution': True, 'enlistment': True,
                'rollback': True, 'trusted_worker_scope': True, 'cleanup': True,
                'owned_threads_remaining': 0, 'active_owner_operations': 0, 'concurrent_identity_checks': 32}

    def check(self, data):
        security.check_receipt(data, 'current', {'fixture_source': {'source_sha': 'source', 'source_tree': 'tree'}}, 'webflux', 'hash')

    def test_complete_inventory_accepted(self):
        self.assertEqual(30, len(security.NEGATIVE_CASES))
        self.check(self.receipt())

    def test_stale_source_artifact_and_run_rejected(self):
        for field in ('run_id', 'fixture_source', 'fixture_tree', 'artifact_source', 'manifest_sha256', 'mode'):
            with self.subTest(field=field):
                data = self.receipt(); data[field] = 'different'
                with self.assertRaises(ValueError): self.check(data)

    def test_missing_duplicate_or_false_denial_cannot_qualify(self):
        for change in ('missing', 'duplicate', 'false'):
            data = self.receipt()
            if change == 'missing': data['negative_cases'].pop()
            elif change == 'duplicate': data['negative_cases'][0] = copy.deepcopy(data['negative_cases'][1])
            else: data['negative_cases'][0]['side_effects_unchanged'] = False
            with self.assertRaises(ValueError): self.check(data)

    def test_failures_and_incomplete_cleanup_or_concurrency_rejected(self):
        for field, value in [('status', 'FAILED'), ('owner_calls', 5), ('committed_reconciliations', 3),
                             ('cleanup', False), ('enlistment', False), ('trusted_worker_scope', False),
                             ('owned_threads_remaining', 1), ('active_owner_operations', 1), ('concurrent_identity_checks', 0)]:
            with self.subTest(field=field):
                data = self.receipt(); data[field] = value
                with self.assertRaises(ValueError): self.check(data)
