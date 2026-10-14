"""Export pinned upstream sources/notices, never local games or untracked files."""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
PIN = "11f5b6cd141c0884a130ee45743f1f5e17bb1783"


def git(repo, *args):
    return subprocess.check_output(["git", "-C", str(repo), *args])


def write_notice(path, text):
    # Upstream archives mix LF and CRLF; avoid CRCRLF on Windows exports.
    normalized = "\n".join(line.rstrip() for line in text.splitlines()).rstrip() + "\n"
    path.write_bytes(normalized.encode("utf-8"))


def repositories(repo, relative=""):
    revision = git(repo, "rev-parse", "HEAD").decode().strip()
    subprocess.run(["git", "-C", str(repo), "diff", "--quiet", "HEAD", "--", "."], check=True)
    yield repo, relative, revision
    for record in git(repo, "ls-files", "--stage", "-z").split(b"\0"):
        if not record:
            continue
        meta, path = record.split(b"\t", 1)
        mode, expected, _ = meta.split()
        if mode == b"160000":
            child = path.decode()
            actual = git(repo / child, "rev-parse", "HEAD").strip()
            if actual != expected:
                raise RuntimeError("Submodule revision mismatch: " + child)
            yield from repositories(repo / child, "/".join(filter(None, [relative, child])))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", type=pathlib.Path, default=ROOT / "local-games/eka2l1-audit/upstream")
    parser.add_argument("--libuv", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, default=ROOT / "build/symbian-sources")
    args = parser.parse_args()
    if git(args.upstream, "rev-parse", "HEAD").decode().strip() != PIN:
        raise RuntimeError("Unexpected EKA2L1 revision")
    args.output.mkdir(parents=True, exist_ok=True)
    notice_root = ROOT / "engines/symbian/core/src/main/assets/licenses/symbian"
    notice_root.mkdir(parents=True, exist_ok=True)
    repos = list(repositories(args.upstream))
    repos.append((args.libuv, "abyss-libuv", git(args.libuv, "rev-parse", "HEAD").decode().strip()))
    records = []
    # Sound banks are independent assets; the distributed host uses GeneralUser GS.
    excludes = [":(glob,exclude)**/*.sf2", ":(glob,exclude)**/*.hsb",
                ":(glob,exclude)**/*.mpn", ":(glob,exclude)**/*.sis", ":(glob,exclude)**/*.sisx",
                ":(glob,exclude)**/*.n-gage", ":(glob,exclude)**/*.rom", ":(glob,exclude)**/*.rpkg",
                ":(exclude)minibae/banks", ":(exclude)minibae/Tools"]
    with tempfile.TemporaryDirectory(dir=args.output) as temporary:
        for index, (repo, path, revision) in enumerate(repos):
            name = f"{index:02d}-{path.replace('/', '_') or 'eka2l1'}.zip"
            archive = args.output / name
            subprocess.run(["git", "-C", str(repo), "archive", "--format=zip", "-o", str(archive),
                            revision, "--", ".", *excludes], check=True)
            records.append(dict(path=path, revision=revision, archive=name,
                                sha256=hashlib.sha256(archive.read_bytes()).hexdigest()))
            notices = []
            with zipfile.ZipFile(archive) as source:
                for entry in source.infolist():
                    base = pathlib.PurePosixPath(entry.filename).name.lower()
                    if entry.file_size < 2_000_000 and (base.startswith(("license", "copying", "notice")) or base == "copyright"):
                        notices.append(f"--- {entry.filename} ---\n" + source.read(entry).decode("utf-8", errors="replace"))
            if notices:
                write_notice(notice_root / (name[:-4] + ".txt"),
                    f"Source: EKA2L1/{path or '.'}\nRevision: {revision}\n\n" + "\n\n".join(notices))
            print(name, flush=True)
    lock = dict(eka2l1=PIN, components=records, excluded="Unshipped sound banks, miniBAE tools/banks and game/phone packages")
    (args.output / "sources.json").write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
    (ROOT / "engines/symbian/source-lock.json").write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
    headers = ["src/external/TinySoundFont/tsf.h", "src/external/TinySoundFont/tml.h",
               "src/external/glad/include/KHR/khrplatform.h", "src/external/glad/include/glad/glad.h",
               "src/external/sqlite3/sqlite3.c"]
    notices = []
    for name in headers:
        text = (args.upstream / name).read_text(encoding="utf-8", errors="replace")
        for block in re.findall(r"/\*[\s\S]*?\*/", text[:32000]):
            if re.search(r"copyright|permission|public.domain|blessing", block, re.I):
                notices.append(f"--- {name} ---\n{block.strip()}")
    write_notice(notice_root / "embedded-headers.txt", "\n\n".join(notices))


if __name__ == "__main__":
    main()
