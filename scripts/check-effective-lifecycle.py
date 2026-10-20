#!/usr/bin/env python3
"""Compare Maven's resolved parent goal bindings with and without explicit full."""
from pathlib import Path
import os
import subprocess
import xml.etree.ElementTree as ET
from importlib.util import module_from_spec, spec_from_file_location

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'target/qm'
OUT.mkdir(parents=True, exist_ok=True)
WRAPPER = str(ROOT / ('mvnw.cmd' if os.name == 'nt' else 'mvnw'))
SETTINGS = os.environ.get('MODELSYNC_MAVEN_SETTINGS')
SETTINGS_ARGS = ['-s', str(ROOT / SETTINGS)] if SETTINGS else []
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}


def goals(path: Path) -> set[tuple[str, str]]:
    root = ET.parse(path).getroot()
    return {(plugin.findtext('m:artifactId', namespaces=NS), goal.text)
            for plugin in root.findall('m:build/m:plugins/m:plugin', NS)
            for goal in plugin.findall('m:executions/m:execution/m:goals/m:goal', NS)}


for name, profile in (('default', []), ('full', ['-Pfull'])):
    output = OUT / f'effective-{name}.xml'
    result = subprocess.run([WRAPPER, '-B', '-ntp', *SETTINGS_ARGS, '-N', *profile,
                             'help:effective-pom', f'-Doutput={output}'], cwd=ROOT,
                            capture_output=True, text=True, check=False)
    if result.returncode or not output.is_file():
        raise SystemExit(f'Effective {name} POM unavailable: Maven exit {result.returncode}')

default = goals(OUT / 'effective-default.xml')
full = goals(OUT / 'effective-full.xml')
expensive = {'pitest-maven', 'gatling-maven-plugin', 'maven-failsafe-plugin'}
if any(artifact in expensive for artifact, _ in default):
    raise SystemExit(f'Expensive goal bound in effective default parent: {default}')
if ('maven-failsafe-plugin', 'integration-test') not in full or not default.issubset(full):
    raise SystemExit('Effective full parent lost default goals or Oracle Failsafe binding')
output = OUT / 'effective-full-sample.xml'
result = subprocess.run([WRAPPER, '-B', '-ntp', *SETTINGS_ARGS, '-N', '-Pfull',
                         '-f', str(ROOT / 'model-sync-sample/pom.xml'),
                         'help:effective-pom', f'-Doutput={output}'], cwd=ROOT,
                        capture_output=True, text=True, check=False)
if result.returncode or not output.is_file():
    raise SystemExit(f'Effective full sample POM unavailable: Maven exit {result.returncode}')
spec = spec_from_file_location('profile_rules', ROOT / 'scripts/check-profiles.py')
rules = module_from_spec(spec)
spec.loader.exec_module(rules)
rules.compiled_failsafe_classpath(ET.parse(output).getroot(), ROOT / 'model-sync-sample/target/classes')
print('EFFECTIVE LIFECYCLE PASS: default parent has no expensive goals; full retains default and adds Failsafe')
print('EFFECTIVE SAMPLE CLASSPATH PASS: Failsafe uses compiled application classes after Boot repackaging')
