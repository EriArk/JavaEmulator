"""Stage the patched touchHLE source build and matching official Android resources."""
import argparse
import io
from pathlib import Path
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
with zipfile.ZipFile(args.release_zip) as release:
    apk_name = next(n for n in release.namelist() if n.endswith(".apk"))
    with zipfile.ZipFile(io.BytesIO(release.read(apk_name))) as apk:
        (jni / "libSDL2.so").write_bytes(apk.read("lib/arm64-v8a/libSDL2.so"))
        for name in apk.namelist():
            if name.startswith("assets/touchHLE") and not name.endswith("/"):
                apk.extract(name, out)
    notices = out / "assets/licenses/iphone"
    notices.mkdir(exist_ok=True, parents=True)
    copying = next(n for n in release.namelist() if n.endswith("COPYING.txt"))
    (notices / "COPYING.txt").write_bytes(release.read(copying))
    shutil.copy2(args.source / "LICENSE", notices / "MPL-2.0.txt")
    shutil.copy2(args.source / "vendor/SDL/LICENSE.txt", notices / "SDL.txt")
