"""Prepare a pinned self-hosted Ruffle player for the optional Flash Lite build."""
import base64
import hashlib
import io
import tarfile
import urllib.request
from pathlib import Path

URL = "https://registry.npmjs.org/@ruffle-rs/ruffle/-/ruffle-0.8.0.tgz"
INTEGRITY = "zN3i1HFjSrH0VhdUlz0c5Fd2aVqYz4pgQ4UlmmeJ/GdCcIdgIl4uWOdPwG+LoU/4JuuNAnPecjtoLbgiSTRCCw=="
root = Path(__file__).resolve().parents[2] / "build/flash/assets"
with urllib.request.urlopen(URL, timeout=60) as response:
    archive = response.read()
if base64.b64encode(hashlib.sha512(archive).digest()).decode() != INTEGRITY:
    raise ValueError("Ruffle package checksum mismatch")
with tarfile.open(fileobj=io.BytesIO(archive), mode="r:gz") as package:
    for entry in package:
        name = Path(entry.name).name
        if entry.isfile() and (name == "ruffle.js" or name.endswith(".wasm")):
            target = root / "flash" / name
        elif entry.isfile() and name in ("LICENSE_MIT", "LICENSE_APACHE"):
            target = root / "licenses" / ("ruffle-" + name + ".txt")
        else:
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(package.extractfile(entry).read())
