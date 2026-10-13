# RMS Upgrade Probe

An original Apache-2.0 MIDlet fixture for cross-APK save checks. It is not a
game or an app asset, and its generated JAR stays in ignored `../build`.

Build a normal host's Java classes first, then:

```powershell
./build.ps1 -HostClasses /path/to/app/build/intermediates/javac/fdroidDebug/compileFdroidDebugJavaWithJavac/classes
```

Use a JDK 21 `JAVA_HOME`. The fixture targets Java 8 bytecode supported by dx;
this deliberately exercises stack-map handling in the importer too.

1. Install the baseline **rehearsal** APK, import `UpgradeProbe.jar` normally.
2. Press `5` several times; record `Saved`. Each press commits and closes RMS.
3. Press `0` to exit. Snapshot the fixture's config and RMS file hashes.
4. Install the candidate rehearsal APK using `adb install -r`, without clearing
   data or reinstalling the MIDlet. Compare file hashes **before** launching it.
5. Open the existing library item: `Loaded` and `Saved` must equal the old value.
6. Increment, exit and reopen to prove that the restored store remains writable.

Use different values for Handheld and Phone. The harness packages are isolated
from normal AbyssME, but baseline/candidate intentionally share a rehearsal ID
and debug signing key. This is not a production-key or every-game save guarantee.
Keep commercial game archives and save payloads out of public evidence.

For an actual historical beta.2 upgrade, pass `-LegacyAsmJar /path/to/asm-9.8.jar`
to `build.ps1`. This original fixture uses only Java 5-compatible instructions;
the helper changes its class version to 49 and removes stack frames, because
beta.2 predates the framed-bytecode importer fix. It rejects invokedynamic and
is not a general game converter. Install the historical signed APK only in an
empty test instance, import through the system picker, and update using the
same release signing key. Never downgrade or clear a user's existing library.
