"""Offline tests for publication guards and test-report accounting, not Maven tests."""
from pathlib import Path
import importlib.util
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]

def load(name: str, path: str):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

publish = load('publication', 'check-snapshot-publication.py')
reports = load('reports', 'check-test-reports.py')

class PublicationChecks(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        shutil.copyfile(ROOT / 'pom.xml', self.root / 'pom.xml')
        for pom in ROOT.glob('model-sync-*/pom.xml'):
            dest = self.root / pom.relative_to(ROOT)
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(pom, dest)
        self.env = {'GITHUB_REPOSITORY': 'Tobia5/model-sync-starter',
                    'GITHUB_REF': 'refs/heads/main', 'GITHUB_EVENT_NAME': 'workflow_dispatch',
                    'SNAPSHOT_CONFIRMATION': 'PUBLISH_SNAPSHOT'}

    def test_valid_snapshot(self):
        self.assertEqual('0.1.0-SNAPSHOT', publish.check(self.root, self.env))

    def test_wrong_repository(self):
        self.env['GITHUB_REPOSITORY'] = 'someone/else'
        with self.assertRaises(ValueError): publish.check(self.root, self.env)

    def test_non_main(self):
        self.env['GITHUB_REF'] = 'refs/heads/unreviewed'
        with self.assertRaises(ValueError): publish.check(self.root, self.env)

    def test_pr_cannot_publish(self):
        self.env['GITHUB_EVENT_NAME'] = 'pull_request'
        with self.assertRaises(ValueError): publish.check(self.root, self.env)

    def test_confirmation_required(self):
        self.env['SNAPSHOT_CONFIRMATION'] = ''
        with self.assertRaises(ValueError): publish.check(self.root, self.env)

    def test_release_rejected(self):
        p = self.root / 'pom.xml'; p.write_text(p.read_text().replace('0.1.0-SNAPSHOT', '0.1.0'))
        with self.assertRaises(ValueError): publish.check(self.root, self.env)

    def test_destination_change_rejected(self):
        p = self.root / 'pom.xml'; p.write_text(p.read_text().replace('maven.pkg.github.com/tobia5', 'example.invalid/else'))
        with self.assertRaises(ValueError): publish.check(self.root, self.env)

    def test_sample_cannot_deploy(self):
        p = self.root / 'model-sync-sample/pom.xml'; p.write_text(p.read_text().replace('<maven.deploy.skip>true', '<maven.deploy.skip>false'))
        with self.assertRaises(ValueError): publish.check(self.root, self.env)

    def test_child_version_drift(self):
        p = self.root / 'model-sync-core/pom.xml'; p.write_text(p.read_text().replace('0.1.0-SNAPSHOT', '0.2.0-SNAPSHOT'))
        with self.assertRaises(ValueError): publish.check(self.root, self.env)

class ReportChecks(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        src = self.root / 'model-sync-core/src/test/java/io/modelsync/ExampleTest.java'
        src.parent.mkdir(parents=True); src.write_text('class ExampleTest {}')
        self.target = self.root / 'model-sync-core/target/surefire-reports/TEST-io.modelsync.ExampleTest.xml'
        self.target.parent.mkdir(parents=True)

    def write(self, tests=1, skipped=0, errors=0, failures=0, name='ExampleTest'):
        self.target.write_text(f'<testsuite name="io.modelsync.{name}" tests="{tests}" skipped="{skipped}" errors="{errors}" failures="{failures}"/>')

    def test_pass(self):
        self.write(); self.assertEqual((1, 1), reports.check(self.root))

    def test_missing(self):
        with self.assertRaises(ValueError): reports.check(self.root)

    def test_empty(self):
        self.write(tests=0)
        with self.assertRaises(ValueError): reports.check(self.root)

    def test_skipped(self):
        self.write(skipped=1)
        with self.assertRaises(ValueError): reports.check(self.root)

    def test_error(self):
        self.write(errors=1)
        with self.assertRaises(ValueError): reports.check(self.root)

    def test_failure(self):
        self.write(failures=1)
        with self.assertRaises(ValueError): reports.check(self.root)

    def test_missing_expected_class(self):
        self.write(name='WrongTest')
        with self.assertRaises(ValueError): reports.check(self.root)

    def test_malformed(self):
        self.target.write_text('<')
        with self.assertRaises(Exception): reports.check(self.root)

    def test_no_test_modules(self):
        with tempfile.TemporaryDirectory() as empty:
            with self.assertRaises(ValueError): reports.check(Path(empty))

if __name__ == '__main__':
    unittest.main()
