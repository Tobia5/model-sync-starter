#!/usr/bin/env python3
from pathlib import Path
import re, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
ns={'m':'http://maven.apache.org/POM/4.0.0'}
pom=ET.parse(root/'pom.xml')
modules=[m.text for m in pom.findall('m:modules/m:module',ns)]
modules += [m.text for m in pom.findall("m:profiles/m:profile[m:id='full']/m:modules/m:module", ns)]
assert len(modules)==len(set(modules)), 'Duplicate Maven module'
graph={}
for module in modules:
    path=root/module/'pom.xml'
    assert path.exists(), f'Missing module {module}'
    tree=ET.parse(path)
    graph[module]=[]
    for d in tree.findall('.//m:dependency',ns):
        if d.findtext('m:groupId',namespaces=ns)=='io.modelsync':
            target=d.findtext('m:artifactId',namespaces=ns)
            assert target in modules, f'Missing internal dependency {target}'
            graph[module].append(target)
visiting=set();visited=set()
def visit(module):
    assert module not in visiting, f'Maven dependency cycle at {module}'
    if module in visited:return
    visiting.add(module)
    for target in graph[module]:visit(target)
    visiting.remove(module);visited.add(module)
for module in modules:visit(module)
test_only={'model-sync-testkit','model-sync-sample','model-sync-architecture-tests','model-sync-performance'}
adapters={'model-sync-persistence-jdbc','model-sync-persistence-jpa','model-sync-rest','model-sync-webclient','model-sync-reactor','model-sync-spring'}
for module in modules:
    tree=ET.parse(root/module/'pom.xml')
    for d in tree.findall('m:dependencies/m:dependency',ns):
        if d.findtext('m:groupId',namespaces=ns)!='io.modelsync':continue
        target=d.findtext('m:artifactId',namespaces=ns)
        scope=d.findtext('m:scope',namespaces=ns) or 'compile'
        if module not in test_only and target in test_only and scope!='test':
            raise AssertionError(f'{module} has production dependency on test artifact {target}')
        if module in adapters and target in adapters and scope!='test':
            raise AssertionError(f'Adapter-to-adapter dependency: {module} -> {target}')
for path in (root/'model-sync-core/src/main/java').rglob('*.java'):
    for name in re.findall(r'^import\s+(?:static\s+)?([\w.]+)',path.read_text(encoding='utf-8'),re.M):
        assert name.startswith(('java.','javax.','io.modelsync.core.')), f'Framework leak: {name}'
        if '/domain/' in path.as_posix():
            assert name.startswith(('java.','io.modelsync.core.domain.')), f'Domain depends outward: {path.name} -> {name}'
        if '/port/' in path.as_posix():
            assert not name.startswith(('io.modelsync.core.application.','org.springframework.','jakarta.persistence.')), f'Port depends outward: {path.name} -> {name}'
for module in modules:
    for path in (root/module/'src').rglob('*.java'):
        if not any(part in ('main','test') for part in path.parts): continue
        source=path.read_text(encoding='utf-8')
        assert not re.search(r'@Nested\b',source), f'JUnit nested fixture: {path}'
        assert not re.search(r'\bnew\s+[\w.<>?, ]+\([^;{}]*\)\s*\{',source), f'Anonymous type: {path}'
        assert not re.search(r'^\s{2,}(?:(?:public|private|protected|static|final|abstract|sealed|non-sealed)\s+)*(?:class|record|enum|interface|@interface)\s+\w+',source,re.M), f'Nested/local type: {path}'
imports=root/'model-sync-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports'
for name in imports.read_text(encoding='utf-8').splitlines():
    if not name or name.startswith('#'):continue
    assert (root/'model-sync-autoconfigure/src/main/java'/Path(name.replace('.','/')+'.java')).exists(),name
print(f'ARCHITECTURE: {len(modules)} Maven modules, acyclic dependency graph, core imports isolated, auto-configuration imports resolve.')
