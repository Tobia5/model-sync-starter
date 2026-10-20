#!/usr/bin/env python3
"""Fail closed before development snapshot publication. Never prints credentials."""
from __future__ import annotations
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
REPOSITORY = 'Tobia5/model-sync-starter'
URL = 'https://maven.pkg.github.com/tobia5/model-sync-starter'

def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)

def check(root: Path, env: dict[str, str]) -> str:
    require(env.get('GITHUB_REPOSITORY') == REPOSITORY, 'Unexpected repository')
    require(env.get('GITHUB_REF') == 'refs/heads/main', 'Publication requires main')
    require(env.get('GITHUB_EVENT_NAME') == 'workflow_dispatch', 'Manual dispatch required')
    require(env.get('SNAPSHOT_CONFIRMATION') == 'PUBLISH_SNAPSHOT', 'Explicit snapshot confirmation required')
    pom = ET.parse(root / 'pom.xml').getroot()
    version = pom.findtext('m:version', namespaces=NS) or ''
    require(re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.]+)?-SNAPSHOT', version) is not None,
            'Only explicit development SNAPSHOT versions are allowed')
    require(pom.findtext('m:groupId', namespaces=NS) == 'io.modelsync', 'Unexpected groupId')
    require(pom.findtext('m:artifactId', namespaces=NS) == 'model-sync-parent', 'Unexpected parent artifactId')
    profiles = pom.findall('m:profiles/m:profile', NS)
    profile = next((p for p in profiles if p.findtext('m:id', namespaces=NS) == 'github-packages'), None)
    require(profile is not None, 'Missing github-packages profile')
    require(profile.findtext('m:distributionManagement/m:snapshotRepository/m:url', namespaces=NS) == URL,
            'Unexpected publication repository')
    require(profile.find('m:distributionManagement/m:repository', NS) is None,
            'Stable release deployment is not authorized by this workflow')
    require(profile.findtext('m:distributionManagement/m:snapshotRepository/m:id', namespaces=NS) == 'github',
            'Unexpected Maven server ID')
    for module in pom.findall('m:modules/m:module', NS):
        name = module.text or ''
        require(re.fullmatch(r'model-sync-[a-z0-9-]+', name) is not None, 'Invalid module path')
        child = ET.parse(root / name / 'pom.xml').getroot()
        require(child.findtext('m:parent/m:version', namespaces=NS) == version,
                f'Parent version drift: {name}')
        own_version = child.findtext('m:version', namespaces=NS)
        require(own_version in (None, version), f'Module version drift: {name}')
    sample = ET.parse(root / 'model-sync-sample/pom.xml').getroot()
    require(sample.findtext('m:properties/m:maven.deploy.skip', namespaces=NS) == 'true',
            'Sample must not be deployed')
    return version

def main() -> int:
    try:
        version = check(Path(__file__).resolve().parents[1], dict(os.environ))
    except (ValueError, OSError, ET.ParseError) as error:
        print(f'BLOCKED: {error}', file=sys.stderr)
        return 1
    print(f'PASS: approved development snapshot coordinates {version}. Not production qualification.')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
