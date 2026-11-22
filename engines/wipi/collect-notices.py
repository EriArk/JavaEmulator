"""Collect the license declarations and notice texts for the Android Cargo target."""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('manifest', type=Path, help='Path to the pinned rust/Cargo.toml')
parser.add_argument('source_cache', type=Path, help='Cache for upstream notices omitted from crates.io packages')
parser.add_argument('output', type=Path)
args = parser.parse_args()
target = 'aarch64-linux-android'
common = ['--locked', '--manifest-path', str(args.manifest.resolve())]
tree = subprocess.check_output(['cargo', 'tree', *common, '--target', target, '-p', 'wipi_android',
                                '-e', 'normal', '--prefix', 'none', '--format', '{p}'], text=True)
packages = {re.match(r'^(\S+) v([^ ]+)', line).groups() for line in tree.splitlines()}
metadata = json.loads(subprocess.check_output(['cargo', 'metadata', *common, '--filter-platform', target,
                                              '--format-version', '1']))
texts = defaultdict(set)
rows = []
prefixes = ('LICENSE', 'LICENCE', 'COPYING', 'COPYRIGHT', 'NOTICE', 'UNLICENSE')

def notices(root):
    return [f for item in sorted(root.iterdir()) if item.name.upper().startswith(prefixes)
            for f in ([item] if item.is_file() else sorted(item.rglob('*'))) if f.is_file()]

def upstream_notices(pkg, directory):
    vcs = json.loads((directory / '.cargo_vcs_info.json').read_text())
    rev = vcs['git']['sha1']
    folder = args.source_cache / rev
    if not (folder / '.git').is_dir():
        folder.mkdir(parents=True, exist_ok=True)
        subprocess.run(['git', 'init', '-q', str(folder)], check=True)
    if subprocess.run(['git', '-C', str(folder), 'cat-file', '-e', rev], stderr=subprocess.DEVNULL).returncode:
        subprocess.run(['git', '-C', str(folder), 'fetch', '-q', '--depth=1', pkg['repository'], rev], check=True)
    subprocess.run(['git', '-C', str(folder), 'checkout', '-q', '--detach', rev], check=True)
    return notices(folder)

for pkg in sorted(metadata['packages'], key=lambda p: (p['name'], p['version'])):
    key = (pkg['name'], pkg['version'])
    if key not in packages:
        continue
    directory = Path(pkg['manifest_path']).parent
    files = notices(directory)
    if pkg['license_file']:
        files.append(directory / pkg['license_file'])
    if not files and (pkg['source'] or '').startswith('registry+'):
        files = upstream_notices(pkg, directory)
    if not files:
        root = Path(subprocess.check_output(['git', '-C', str(directory), 'rev-parse', '--show-toplevel'],
                                           text=True).strip())
        files = notices(root)
    if not files:
        raise ValueError('Missing license text for ' + pkg['name'])
    refs = []
    for f in files:
        notice = f.read_text(encoding='utf-8-sig').strip()
        ref = hashlib.sha256(notice.encode()).hexdigest()[:16]
        texts[(ref, notice)].add(pkg['name'])
        refs.append(ref)
    rows.append(f"{pkg['name']} {pkg['version']} | {pkg['license']} | {', '.join(sorted(set(refs)))}")
if len(rows) != len(packages):
    raise ValueError('Cargo tree and metadata disagree on the package list')
with args.output.open('w', encoding='utf-8') as out:
    print('WIPI Android dependency notices\n', file=out)
    print('\n'.join(rows), file=out)
    for (ref, notice), names in sorted(texts.items()):
        print(f"\n[{ref}] {', '.join(sorted(names))}\n\n{notice}", file=out)
