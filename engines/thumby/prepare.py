"""Stage the original TinyCircuits editor's offline runtime for a private debug build."""
import argparse
import json
import shutil
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('editor', type=Path)
parser.add_argument('output', type=Path, help='build/thumby/assets')
args = parser.parse_args()
target = args.output / 'thumby'
target.mkdir(parents=True, exist_ok=True)
# Remove files left by the old editor-binary staging process.
for name in ('bootrom.bin', 'emulator-firmware.uf2'):
    (target / name).unlink(missing_ok=True)
for file in (args.editor / 'rp2040js/dist/esm').rglob('*.js'):
    dest = target / 'rp2040' / file.relative_to(args.editor / 'rp2040js/dist/esm')
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(file, dest)
lib = target / 'lib'
lib.mkdir(exist_ok=True)
names = []
for file in sorted((args.editor / 'CoreThumbyFiles/lib').iterdir()):
    if file.suffix in ('.py', '.bin'):
        shutil.copyfile(file, lib / file.name)
        names.append(file.name)
(target / 'lib.json').write_text(json.dumps(names))
licenses = args.output / 'licenses'
licenses.mkdir(exist_ok=True)
for path, name in [('LICENSE', 'thumby-editor'), ('rp2040js/LICENSE', 'thumby-rp2040js')]:
    shutil.copyfile(args.editor / path, licenses / (name + '.txt'))
