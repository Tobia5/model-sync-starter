import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from cooldown_evidence import collect,qualify
from run_evidence import begin,invalidate


class CooldownEvidenceTests(unittest.TestCase):
    def fixture(self,directory):
        root=Path(directory);(root/'.gitignore').write_text('target/\n')
        subprocess.run(['git','init','-q',str(root)],check=True,capture_output=True)
        subprocess.run(['git','-c','user.name=Synthetic','-c','user.email=synthetic@example.invalid','commit','--allow-empty','-qm','fixture'],cwd=root,check=True,capture_output=True)
        run=begin(root);path=root/'model-sync-persistence-jdbc/target/qm/oracle-cooldown.json';path.parent.mkdir(parents=True)
        path.write_text(json.dumps({**run,'scope':'durable-cooldown-small-recovery','status':'FAILED','cases':[{'name':'heldQuotaReleaseWithinBudgetReusesAbsoluteDeadline','status':'FAILED'}]}))
        return root,path

    def test_failing_execution_is_collected_but_cannot_qualify(self):
        with tempfile.TemporaryDirectory() as directory:
            root,path=self.fixture(directory);value=collect(root)
            self.assertEqual('FAILED',value['status']);self.assertEqual(1,len(value['reports']['oracle-cooldown']['cases']))
            with self.assertRaises(ValueError):qualify(root)

    def test_stale_mismatched_and_malformed_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root,path=self.fixture(directory);original=path.read_text()
            os.utime(path,(time.time()-60,time.time()-60))
            with self.assertRaisesRegex(ValueError,'Stale'):collect(root)
            path.write_text(json.dumps({**json.loads(original),'run_id':'earlier'}))
            with self.assertRaisesRegex(ValueError,'mismatch'):collect(root)
            path.write_text('[]')
            with self.assertRaisesRegex(ValueError,'Malformed'):collect(root)

    def test_blocked_followup_cannot_reuse_earlier_publication_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            root,path=self.fixture(directory);value=json.loads(path.read_text());value['status']='PASS';path.write_text(json.dumps(value))
            invalidate(root)
            with self.assertRaisesRegex(ValueError,'marker absent'):collect(root)

    def test_new_launcher_is_small_and_full_remains_unchanged(self):
        from importlib.util import module_from_spec,spec_from_file_location
        root=Path(__file__).resolve().parents[2]
        spec=spec_from_file_location('cooldown_launcher',root/'scripts/full-oracle-container.py');module=module_from_spec(spec);spec.loader.exec_module(module)
        self.assertEqual(['-Pfull','clean','verify'],module.maven_command()[-3:])
        self.assertEqual(['-Pfull','-N','initialize'],module.maven_command(True)[-3:])
        result=subprocess.run([sys.executable,str(root/'scripts/full-oracle-container.py'),'--cooldown-diagnostic','--comparison-accounting-diagnostic'],capture_output=True,text=True)
        self.assertEqual(2,result.returncode)
