"""Stage the compiled engine, guest system files and component notices."""
import argparse
import json
import shutil
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('micropython', type=Path)
parser.add_argument('output', type=Path)
args = parser.parse_args()
mp = args.micropython
engine = mp / 'TinyCircuits-Tiny-Game-Engine'
target = args.output / 'color'
target.mkdir(parents=True, exist_ok=True)
for source, dest in [('micropython.mjs', 'micropython.js'), ('micropython.wasm', 'micropython.wasm')]:
    shutil.copyfile(mp / 'ports/webassembly/build-standard' / source, target / dest)
files = []
for file in sorted((engine / 'filesystem/system').rglob('*')):
    if file.is_file():
        rel = file.relative_to(engine / 'filesystem')
        (target / rel.parent).mkdir(parents=True, exist_ok=True)
        shutil.copyfile(file, target / rel)
        files.append(rel.as_posix())
(target / 'system.json').write_text(json.dumps(files))
licenses = args.output / 'licenses'
licenses.mkdir(exist_ok=True)
for name, path in [('micropython', mp / 'LICENSE'), ('tiny-game-engine', engine / 'LICENSE.txt')] + [
    (name, engine / 'lib' / name / 'LICENSE') for name in ('cglm','bm8563','FastNoiseLite')
]:
    shutil.copyfile(path, licenses / ('color-' + name + '.txt'))
