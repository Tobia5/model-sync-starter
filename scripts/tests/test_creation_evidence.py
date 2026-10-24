import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
from creation_evidence import collect,qualify
from run_evidence import begin,invalidate

class CreationEvidenceTests(unittest.TestCase):
    def fixture(self,directory):
        root=Path(directory);(root/'.gitignore').write_text('target/\n')
        subprocess.run(['git','init','-q',str(root)],check=True,capture_output=True)
        subprocess.run(['git','-c','user.name=Synthetic','-c','user.email=synthetic@example.invalid','commit','--allow-empty','-qm','fixture'],cwd=root,check=True,capture_output=True)
        data={**begin(root),'scope':'compliant-first-creation','status':'FAILED','cases':[{'name':'work-default-commit','status':'FAILED','observed_ms':14}]}
        path=root/'model-sync-sample/target/qm/oracle-first-creation.json';path.parent.mkdir(parents=True);path.write_text(json.dumps(data));return root,path,data
    def test_explicit_selection_is_small_and_exclusive(self):
        runner=(ROOT/'model-sync-sample/src/test/java/io/modelsync/sample/OracleDiagnosticRunner.java').read_text()
        self.assertIn('case "first-creation" -> List.of("io.modelsync.sample.OracleFirstCreationIT");',runner)
        result=subprocess.run([sys.executable,str(ROOT/'scripts/full-oracle-container.py'),'--creation-diagnostic','--quota-diagnostic'],capture_output=True,text=True)
        self.assertEqual(2,result.returncode)
    def test_partial_failure_collects_but_does_not_qualify(self):
        with tempfile.TemporaryDirectory() as d:
            root,path,data=self.fixture(d);self.assertEqual(14,collect(root)['cases'][0]['observed_ms'])
            with self.assertRaisesRegex(ValueError,'incomplete'):qualify(root)
    def test_stale_mismatched_malformed_and_blocked_are_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            root,path,data=self.fixture(d);os.utime(path,(time.time()-60,time.time()-60))
            with self.assertRaisesRegex(ValueError,'Stale'):collect(root)
            path.write_text(json.dumps({**data,'source_sha':'old'}))
            with self.assertRaisesRegex(ValueError,'mismatch'):collect(root)
            path.write_text(json.dumps({**data,'cases':[7]}))
            with self.assertRaisesRegex(ValueError,'Malformed'):collect(root)
            path.write_text(json.dumps({**data,'status':'PASS'}));invalidate(root)
            with self.assertRaisesRegex(ValueError,'marker absent'):collect(root)
