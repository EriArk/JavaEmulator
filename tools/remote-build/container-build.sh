#!/usr/bin/env bash
set -euo pipefail
mode=${1:?Expected unsigned or debug}
case "$mode" in
  unsigned) variant=Release; extra=(-PunsignedRelease=true); guard=(:app:verifyProductionApks) ;;
  debug) variant=Debug; extra=(); guard=() ;;
  *) echo 'Expected unsigned or debug' >&2; exit 2 ;;
esac
test ! -e keystore.properties || { echo 'Signing configuration must not enter the builder' >&2; exit 2; }
mkdir -p "$HOME" "$GRADLE_USER_HOME" /out
revision=$(git rev-parse HEAD)
test -z "$(git status --porcelain --untracked-files=normal)" || { echo 'Source checkout is dirty' >&2; exit 2; }
bash ./gradlew --no-daemon --console=plain --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m -Djava.io.tmpdir=/tmp' \
  -PsymbianRelease=true -PsymbianSourceDir=/sources/symbian -PnativeBuildJobs=1 \
  "${extra[@]}" ":app:assembleFdroid${variant}" "${guard[@]}" \
  :app:testFdroidDebugUnitTest --rerun :dexlib:testDebugUnitTest --rerun
python3 - "$mode" "$revision" <<'PY'
import hashlib, json, pathlib, shutil, sys, xml.etree.ElementTree as ET
mode, revision = sys.argv[1:]
variant = 'release' if mode == 'unsigned' else 'debug'
folder = pathlib.Path('app/build/symbian-release/outputs/apk/fdroid') / variant
meta = json.loads((folder / 'output-metadata.json').read_text())
assert len(meta['elements']) == 1
source = folder / meta['elements'][0]['outputFile']
target = pathlib.Path('/out') / source.name
shutil.copy2(source, target)
counts = dict(tests=0, failures=0, errors=0, skipped=0)
for folder in ('app/build/symbian-release/test-results/testFdroidDebugUnitTest',
               'dexlib/build/test-results/testDebugUnitTest'):
    reports = list(pathlib.Path(folder).glob('TEST-*.xml'))
    assert reports, folder
    for report in reports:
        root = ET.parse(report).getroot()
        for name in counts:
            counts[name] += int(root.attrib.get(name, 0))
        shutil.copy2(report, pathlib.Path('/out') / report.name)
assert counts['tests'] and not counts['failures'] and not counts['errors']
digest = hashlib.sha256(target.read_bytes()).hexdigest()
pathlib.Path('/out/SHA256SUMS.txt').write_text(f'{digest}  {target.name}\n')
pathlib.Path('/out/BUILD-INFO.json').write_text(json.dumps(dict(
    revision=revision, mode=mode, apk=target.name, sha256=digest,
    applicationId=meta['applicationId'], version=meta['elements'][0]['versionName'],
    tests=counts, device_tested=False, production_signed=False), indent=2) + '\n')
print(json.dumps(counts))
PY
