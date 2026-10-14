"""Restore the release source bundle into the same layout as the pinned checkout."""
import argparse
import hashlib
import json
import pathlib
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("bundle", type=pathlib.Path, help="Directory containing sources.json and component ZIPs")
    parser.add_argument("destination", type=pathlib.Path)
    args = parser.parse_args()
    root = args.destination.resolve()
    if root.exists() and any(root.iterdir()):
        raise RuntimeError("Choose an empty destination; existing checkouts are never overwritten")
    lock = json.loads((args.bundle / "sources.json").read_text(encoding="utf-8"))
    for item in lock["components"]:
        path = args.bundle / item["archive"]
        if path.parent.resolve() != args.bundle.resolve() or hashlib.sha256(path.read_bytes()).hexdigest() != item["sha256"]:
            raise RuntimeError("Source archive hash/path mismatch")
    root.mkdir(parents=True, exist_ok=True)
    for item in lock["components"]:
        target = root / item["path"]
        if not target.resolve().is_relative_to(root):
            raise RuntimeError("Invalid component path")
        with zipfile.ZipFile(args.bundle / item["archive"]) as archive:
            for entry in archive.infolist():
                if not (target / entry.filename).resolve().is_relative_to(root):
                    raise RuntimeError("Unsafe archive path")
            archive.extractall(target)
    (root / "ABYSS_REVISION").write_text(lock["eka2l1"] + "\n", encoding="ascii")
    # Upstream's Android copy command still names these unused banks. Empty build
    # placeholders satisfy that command; curated APK assets exclude both names.
    for name in ("defaultbank.sf2", "defaultbank.hsb"):
        placeholder = root / "src/emu/drivers/resources" / name
        if not placeholder.exists():
            placeholder.write_bytes(b"")
    print(root)


if __name__ == "__main__":
    main()
