#!/usr/bin/env bash
set -euo pipefail
# These negative checks run before compilation and never have signing keys.
base=(--no-daemon --console=plain --max-workers=1
  '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m -Djava.io.tmpdir=/tmp'
  -PsymbianRelease=true -PsymbianSourceDir=/sources/symbian)
expect_failure() {
  local name=$1 expected=$2 result=0
  shift 2
  bash ./gradlew "${base[@]}" "$@" > "/out/guard-$name.log" 2>&1 || result=$?
  if (( result == 0 )) || ! grep -Fq "$expected" "/out/guard-$name.log"; then
    echo "Build guard failed: $name; inspect retained log" >&2
    return 1
  fi
  echo "Build guard passed: $name"
}
expect_failure missing-signing 'Release signing is not configured' :app:preFdroidReleaseBuild
expect_failure native-job-limit 'nativeBuildJobs must be an integer from 1 to 8' -PnativeBuildJobs=0 help
expect_failure incompatible-modes 'Unsigned export and debug-signed rehearsal are separate modes' \
  -PunsignedRelease=true -PsymbianReleaseCheck=true help
