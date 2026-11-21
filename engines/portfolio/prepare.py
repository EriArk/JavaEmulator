"""Prepare Portfoliator assets from its pinned Git revision."""
import argparse
import shutil
import subprocess
import tarfile
from pathlib import Path

REVISION = "fb3b254a4581b49de5bbe37c90027b1e9b3c4bf7"
ROOT = Path(__file__).resolve().parents[2]


def prepare(checkout):
    target = ROOT / "build/portfolio/assets/portfolio/core"
    archive = subprocess.Popen(
        ["git", "-C", checkout, "archive", REVISION, "LICENSE", "js/emu",
         *["js/ui/" + name + ".js" for name in ("audio", "fat12", "i18n", "dict-en", "statefile", "format", "permdata")]],
        stdout=subprocess.PIPE,
    )
    target.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(fileobj=archive.stdout, mode="r|") as files:
        for member in files:
            if member.isfile():
                destination = target / member.name
                destination.parent.mkdir(parents=True, exist_ok=True)
                with destination.open("wb") as output:
                    shutil.copyfileobj(files.extractfile(member), output)
    if archive.wait():
        raise SystemExit("Cannot read the pinned Portfoliator revision")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("checkout", help="Path to an delysio/portfoliator Git checkout")
    prepare(parser.parse_args().checkout)
