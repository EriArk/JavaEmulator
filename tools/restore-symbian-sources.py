"""Restore the release source bundle into the same layout as the pinned checkout."""
import argparse
import hashlib
import json
import os
import pathlib
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("bundle", type=pathlib.Path, help="Directory containing sources.json and component ZIPs")
    parser.add_argument("destination", type=pathlib.Path)
    parser.add_argument("--prepare-posix", action="store_true",
                        help="Restore executable bits and text line endings in an existing verified source tree")
    args = parser.parse_args()
    root = args.destination.resolve()
    if not args.prepare_posix and root.exists() and any(root.iterdir()):
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
            if not args.prepare_posix:
                archive.extractall(target)
            if os.name == "posix":
                for entry in archive.infolist():
                    if entry.is_dir():
                        continue
                    original = archive.read(entry)
                    path = target / entry.filename
                    bits = (entry.external_attr >> 16) & 0o111
                    normalized = original
                    if b"\r\n" in original and b"\0" not in original:
                        if original.startswith(b"#!") or path.name == "Makefile" or path.suffix in (".sh", ".mak", ".mk"):
                            normalized = original.replace(b"\r\n", b"\n")
                        else:
                            try:
                                original.decode("utf-8")
                                normalized = original.replace(b"\r\n", b"\n")
                            except UnicodeDecodeError:
                                pass
                    if original == normalized and not bits:
                        continue
                    if path.is_symlink() or not path.is_file():
                        raise RuntimeError("Missing or unsafe build input: " + entry.filename)
                    current = path.read_bytes()
                    if current not in (original, normalized):
                        raise RuntimeError("Modified build input retained: " + entry.filename)
                    if current != normalized:
                        path.write_bytes(normalized)
                    path.chmod(path.stat().st_mode | bits)
    if args.prepare_posix:
        print("Prepared verified archive text for POSIX; binary files and local edits preserved")
        return
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
