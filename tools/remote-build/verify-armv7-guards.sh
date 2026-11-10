#!/usr/bin/env bash
set -euo pipefail
# Configuration checks only; no native compilation or signing credentials.
log_dir=${1:-build/armv7-guards}
mkdir -p "$log_dir"
base=(--no-daemon --console=plain --max-workers=1
  '-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m -Djava.io.tmpdir=/tmp')
reject() {
  local name=$1 expected=$2 result=0
  shift 2
  bash ./gradlew "${base[@]}" "$@" > "$log_dir/guard-armv7-$name.log" 2>&1 || result=$?
  if (( result == 0 )) || ! grep -Fq "$expected" "$log_dir/guard-armv7-$name.log"; then
    echo "ARMv7 guard failed: $name (see $log_dir/guard-armv7-$name.log)" >&2; return 1
  fi
  echo "ARMv7 guard passed: $name"
}
reject combined 'ARMv7 is a Java/Mophun target without Symbian or research modes' \
  -Parmv7=true -PsymbianRelease=true help
reject research 'ARMv7 is a Java/Mophun target without Symbian or research modes' \
  -Parmv7=true -Pm3gExperiment=candidate help
reject invalid 'armv7 must be true or false' -Parmv7=auto help
reject phone "'assemblePhoneDebug' not found in project" -Parmv7=true :app:assemblePhoneDebug
