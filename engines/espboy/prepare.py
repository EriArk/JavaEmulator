"""Stage the pinned Little Game Engine SDK runtime for an offline build."""
import argparse
import shutil
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("sdk", type=Path)
parser.add_argument("assets", type=Path)
args = parser.parse_args()
rev = "c225ad0aaf428081e50f105e5d56dbf401cf1fa2"
if subprocess.check_output(["git", "-C", args.sdk, "rev-parse", "HEAD"], text=True).strip() != rev:
    raise SystemExit("Use SDK revision " + rev)
out = args.assets / "espboy"
out.mkdir(parents=True, exist_ok=True)
# Stage pristine sources from Git. Route EEPROM writes through the host's
# synchronous storage bridge and expose lifecycle control over the sound context.
for name in ("cpu.js", "sound.js", "font.js"):
    script = subprocess.check_output(["git", "-C", args.sdk, "show", "HEAD:" + name]).decode("utf-8", errors="replace")
    if name == "cpu.js":
        script = script.replace("reg[r1] = globalJKey;", "reg[r1] = readButtons();")
        script = script.replace("localStorage[name] = array;", "localStorage.setItem(name, array.join(','));")
        script = script.replace("localStorage[name]", "localStorage.getItem(name)")
    if name == "sound.js":
        script = script.replace("initAudio: initAudio,", "initAudio: initAudio, active(on) { if (audio) on ? audio.resume() : audio.suspend(); },")
        script = script.replace("while (c != 0) {", "while (c && i < 65536) {")
    (out / name).write_text(script, encoding="utf-8")
licenses = args.assets / "licenses"
licenses.mkdir(exist_ok=True)
shutil.copyfile(args.sdk / "LICENSE", licenses / "espboy-lge-bsd.txt")
