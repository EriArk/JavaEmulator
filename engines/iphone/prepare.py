"""Stage the patched touchHLE source build and matching official Android resources."""
import argparse
import io
from pathlib import Path
import hashlib
import subprocess
import shutil
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("source", type=Path)
parser.add_argument("release_zip", type=Path)
parser.add_argument("project", type=Path)
args = parser.parse_args()
out = args.project / "build/iphone"
jni = out / "jniLibs/arm64-v8a"
jni.mkdir(exist_ok=True, parents=True)
shutil.copy2(args.source / "target/aarch64-linux-android/release/libtouchHLE.so", jni)
shutil.copytree(args.source / "vendor/SDL/android-project/app/src/main/java/org",
                out / "sdl-java/org", dirs_exist_ok=True)
# Upstream maintains the source links and license files next to each guest binary.
# Compare the APK to the complete pinned directories, including their notices.
pin = "8eb3418b3343bc1e3d1a330b55cb1d708a015721"
entries = subprocess.check_output(["git", "-C", str(args.source), "ls-tree", "-r", pin,
                                   "touchHLE_dylibs", "touchHLE_fonts"], text=True)
expected = {}
for entry in entries.splitlines():
    mode, kind, blob, name = entry.split(maxsplit=3)
    expected["assets/" + name] = blob
with zipfile.ZipFile(args.release_zip) as release:
    apk_name = next(n for n in release.namelist() if n.endswith(".apk"))
    with zipfile.ZipFile(io.BytesIO(release.read(apk_name))) as apk:
        (jni / "libSDL2.so").write_bytes(apk.read("lib/arm64-v8a/libSDL2.so"))
        actual = {n for n in apk.namelist() if n.startswith(("assets/touchHLE_dylibs/", "assets/touchHLE_fonts/"))
                  and not n.endswith("/")}
        if actual != expected.keys():
            raise ValueError("Guest library/font inventory differs from touchHLE " + pin)
        for name in sorted(actual):
            data = apk.read(name)
            if hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest() != expected[name]:
                raise ValueError("Guest resource differs from pinned source: " + name)
        for name in apk.namelist():
            if name.startswith("assets/touchHLE") and not name.endswith("/"):
                apk.extract(name, out)
    notices = out / "assets/licenses/iphone"
    notices.mkdir(exist_ok=True, parents=True)
    copying = next(n for n in release.namelist() if n.endswith("COPYING.txt"))
    (notices / "COPYING.txt").write_bytes(release.read(copying))
    shutil.copy2(args.source / "LICENSE", notices / "MPL-2.0.txt")
    shutil.copy2(args.source / "vendor/SDL/LICENSE.txt", notices / "SDL.txt")
