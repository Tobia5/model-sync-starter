#!/usr/bin/env python3
"""Create and validate the identity of one clean Maven verification run."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(['git', *args], cwd=root, text=True).strip()


def identity(root: Path) -> dict:
    return {
        'source_sha': git(root, 'rev-parse', 'HEAD'),
        'source_tree': git(root, 'rev-parse', 'HEAD^{tree}'),
        'source_change_digest': hashlib.sha256(git(root, 'status', '--porcelain', '--untracked-files=normal').encode()).hexdigest(),
    }


def begin(root: Path) -> dict:
    data = {'run_id': str(uuid.uuid4()), 'started_ns': time.time_ns(), **identity(root)}
    destination = root / 'target/qm/run-context.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(data, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    return data


def current(root: Path) -> dict:
    marker = root / 'target/qm/run-context.json'
    if not marker.is_file():
        raise ValueError('Run identity marker absent')
    try:
        data = json.loads(marker.read_text(encoding='utf-8'))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError('Malformed run identity marker') from error
    if not isinstance(data, dict) or not isinstance(data.get('run_id'), str) or not data['run_id']:
        raise ValueError('Run identifier absent')
    if not isinstance(data.get('started_ns'), int) or data['started_ns'] <= 0:
        raise ValueError('Run start time absent')
    for key, value in identity(root).items():
        if data.get(key) != value:
            raise ValueError(f'Run marker source identity mismatch: {key}')
    return data


def fresh(path: Path, root: Path) -> None:
    run = current(root)
    if not path.is_file() or path.stat().st_mtime_ns < run['started_ns']:
        raise ValueError(f'Stale or absent report for run {run["run_id"]}: {path}')


def reactor_modules(root: Path, full: bool = False) -> list[Path]:
    if not (root / 'pom.xml').is_file():
        raise ValueError('Reactor POM absent')
    document = ET.parse(root / 'pom.xml')
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    names = [m.text for m in document.findall('m:modules/m:module', ns)]
    if full:
        names += [m.text for m in document.findall("m:profiles/m:profile[m:id='full']/m:modules/m:module", ns)]
    return [root / name for name in names]


if __name__ == '__main__':
    print(json.dumps(begin(Path(__file__).resolve().parents[1]), sort_keys=True))
