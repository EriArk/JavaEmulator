"""Small cross-platform checks for the public source-bundle restore contract."""
import hashlib
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest
import zipfile

RESTORE = pathlib.Path(__file__).with_name("restore-symbian-sources.py")


class RestoreSourcesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        self.bundle = self.root / "bundle"
        self.bundle.mkdir()
        self.dest = self.root / "source"
        self.make_bundle()

    def make_bundle(self, filename="configure", component=""):
        archive = self.bundle / "component.zip"
        with zipfile.ZipFile(archive, "w") as stream:
            info = zipfile.ZipInfo(filename)
            info.create_system = 3
            info.external_attr = 0o100755 << 16
            stream.writestr(info, "#!/bin/sh\nexit 0\n")
            stream.writestr("src/emu/drivers/resources/README", "fixture")
            stream.writestr("Makefile", "all:\r\n\ttrue\r\n")
            stream.writestr("list.c", "extern int codec;\r\n")
            stream.writestr("binary.bin", b"\0\r\n\xff")
        lock = {"eka2l1": "test-revision", "components": [{"archive": archive.name,
                "path": component, "sha256": hashlib.sha256(archive.read_bytes()).hexdigest()}]}
        (self.bundle / "sources.json").write_text(json.dumps(lock))

    def run_restore(self, repair=False):
        args = [sys.executable, str(RESTORE), str(self.bundle), str(self.dest)]
        if repair:
            args.append("--prepare-posix")
        return subprocess.run(args, capture_output=True, text=True)

    def test_restore_and_refuse_overwrite(self):
        result = self.run_restore()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.dest / "ABYSS_REVISION").read_text().strip(), "test-revision")
        self.assertNotEqual(self.run_restore().returncode, 0)

    @unittest.skipUnless(os.name == "posix", "Unix executable bits")
    def test_executable_bits_and_repair(self):
        self.assertEqual(self.run_restore().returncode, 0)
        script = self.dest / "configure"
        self.assertTrue(script.stat().st_mode & 0o111)
        original = script.read_bytes()
        script.chmod(0o644)
        self.assertEqual(self.run_restore(repair=True).returncode, 0)
        self.assertTrue(script.stat().st_mode & 0o111)
        self.assertEqual(script.read_bytes(), original)
        self.assertNotIn(b"\r", (self.dest / "Makefile").read_bytes())
        self.assertNotIn(b"\r", (self.dest / "list.c").read_bytes())
        self.assertEqual((self.dest / "binary.bin").read_bytes(), b"\0\r\n\xff")

    @unittest.skipUnless(os.name == "posix", "Unix executable bits")
    def test_modified_script_is_preserved(self):
        self.assertEqual(self.run_restore().returncode, 0)
        script = self.dest / "configure"
        script.write_text("local edits\n")
        script.chmod(0o644)
        self.assertNotEqual(self.run_restore(repair=True).returncode, 0)
        self.assertEqual(script.read_text(), "local edits\n")
        self.assertFalse(script.stat().st_mode & 0o111)

    def test_hash_mismatch(self):
        with (self.bundle / "component.zip").open("ab") as stream:
            stream.write(b"changed")
        self.assertNotEqual(self.run_restore().returncode, 0)
        self.assertFalse(self.dest.exists())

    def test_archive_traversal(self):
        self.make_bundle("../escape")
        self.assertNotEqual(self.run_restore().returncode, 0)
        self.assertFalse((self.root / "escape").exists())

    def test_component_traversal(self):
        self.make_bundle(component="../escape")
        self.assertNotEqual(self.run_restore().returncode, 0)
        self.assertFalse((self.root / "escape").exists())


if __name__ == "__main__":
    unittest.main()
