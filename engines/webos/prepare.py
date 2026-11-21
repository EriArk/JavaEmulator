"""Prepare the original Enyo 1 framework assets from its pinned Git revision."""
import argparse
import shutil
import subprocess
import tarfile
from pathlib import Path

REVISION = "52886009e73b665a94a65c5a66b5f285d2cf321d"
ROOT = Path(__file__).resolve().parents[2]


def prepare(checkout):
    target = ROOT / "build/webos/assets/webos/enyo"
    archive = subprocess.Popen(
        ["git", "-C", checkout, "archive", REVISION, "LICENSE-2.0.txt",
         "framework/enyo.js", "framework/build", "framework/source", "framework/lib"],
        stdout=subprocess.PIPE,
    )
    target.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(fileobj=archive.stdout, mode="r|") as files:
        for member in files:
            if member.isfile():
                destination = target / member.name.removeprefix("framework/")
                destination.parent.mkdir(parents=True, exist_ok=True)
                with destination.open("wb") as output:
                    shutil.copyfileobj(files.extractfile(member), output)
    if archive.wait():
        raise SystemExit("Cannot read the pinned Enyo revision")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("checkout", help="Path to an enyojs/enyo-1.0 Git checkout")
    prepare(parser.parse_args().checkout)
