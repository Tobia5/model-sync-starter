#!/usr/bin/env python3
from pathlib import Path
import re, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
ns={'m':'http://maven.apache.org/POM/4.0.0'}
pom=ET.parse(root/'pom.xml')
modules=[m.text for m in pom.findall('m:modules/m:module',ns)]
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
for path in (root/'model-sync-core/src/main/java').rglob('*.java'):
    for name in re.findall(r'^import\s+(?:static\s+)?([\w.]+)',path.read_text(),re.M):
        assert name.startswith(('java.','javax.','io.modelsync.core.')), f'Framework leak: {name}'
imports=root/'model-sync-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports'
for name in imports.read_text().splitlines():
    if not name or name.startswith('#'):continue
    assert (root/'model-sync-autoconfigure/src/main/java'/Path(name.replace('.','/')+'.java')).exists(),name
print(f'ARCHITECTURE: {len(modules)} Maven modules, acyclic dependency graph, core imports isolated, auto-configuration imports resolve.')
