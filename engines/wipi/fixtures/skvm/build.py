# SPDX-License-Identifier: GPL-3.0-only
"""Build the SKVM fixture with JDK 11+ and the mobile adapter's MIDP stubs."""
from pathlib import Path
import io
import subprocess
import sys
import tempfile
import zipfile

here = Path(__file__).resolve().parent
upstream, output = map(Path, sys.argv[1:])
descriptor = b"MIDlet-Name: Slider\nMIDlet-1: Slider,,Slider\nDD-ProgName: Slider\n"
with tempfile.TemporaryDirectory() as classes:
    stubs = [*(upstream / "demo/stubs").rglob("*.java"), *(here / "stubs").rglob("*.java")]
    subprocess.run(["javac", "--release", "8", "-d", classes, *stubs, here / "Slider.java"], check=True)
    jar = io.BytesIO()
    with zipfile.ZipFile(jar, "w") as game:
        game.writestr("META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\n" + descriptor + b"\n")
        for compiled in sorted(Path(classes).glob("Slider*.class")):
            game.write(compiled, compiled.name)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as package:
        package.writestr("slider.msd", descriptor)
        package.writestr("slider.jar", jar.getvalue())
