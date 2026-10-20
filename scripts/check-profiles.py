#!/usr/bin/env python3
"""Audit bound Maven goals in the declared default and full lifecycles."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
EXPENSIVE = {'pitest-maven', 'gatling-maven-plugin', 'maven-failsafe-plugin'}

def plugins(node):
    result = []
    for plugin in node.findall('m:build/m:plugins/m:plugin', NS):
        artifact = plugin.findtext('m:artifactId', namespaces=NS)
        for goal in plugin.findall('m:executions/m:execution/m:goals/m:goal', NS):
            result.append((artifact, goal.text))
    return result

def check(root=ROOT):
    pom = ET.parse(root / 'pom.xml').getroot()
    base = plugins(pom)
    profiles = {p.findtext('m:id', namespaces=NS): p for p in pom.findall('m:profiles/m:profile', NS)}
    if 'full' not in profiles:
        raise ValueError('Missing explicit full profile')
    full = profiles['full']
    if full.find('m:activation', NS) is not None:
        raise ValueError('Full profile must be explicitly selected, never activated implicitly')
    for item in base:
        if item[0] in EXPENSIVE:
            raise ValueError(f'Expensive goal bound in default build: {item}')
    for profile_name, profile in profiles.items():
        if profile_name != 'full' and any(a in EXPENSIVE for a, _ in plugins(profile)):
            raise ValueError(f'Expensive goal bound outside full: {profile_name}')
    full_goals = plugins(full)
    if ('exec-maven-plugin', 'exec') not in base:
        raise ValueError('Default static audit is not bound')
    module_names = [m.text for m in pom.findall('m:modules/m:module', NS)]
    module_names += [m.text for m in full.findall('m:modules/m:module', NS)]
    for module_name in module_names:
        child = ET.parse(root / module_name / 'pom.xml').getroot()
        if any(a in EXPENSIVE for a, _ in plugins(child)):
            raise ValueError(f'Expensive goal in default module {module_name}')
        for profile in child.findall('m:profiles/m:profile', NS):
            profile_name = profile.findtext('m:id', namespaces=NS)
            goals = plugins(profile)
            if profile_name != 'full' and any(a in EXPENSIVE for a, _ in goals):
                raise ValueError(f'Expensive goal outside full: {module_name}:{profile_name}')
            if profile_name == 'full':
                if profile.find('m:activation', NS) is not None:
                    raise ValueError(f'Implicit full activation: {module_name}')
                full_goals += goals
    for artifact in EXPENSIVE:
        if artifact not in {a for a, _ in full_goals}:
            raise ValueError(f'Full profile missing {artifact}')
    return base, full_goals

if __name__ == '__main__':
    default, full = check()
    print(f'PROFILE AUDIT PASS: default goals={default}; full additions={full}')
