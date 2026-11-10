#!/usr/bin/env bash
set -euo pipefail
revision=${1:?Pass a full committed revision}
mode=${2:-unsigned}
target=${3:-combined}
[[ "$revision" =~ ^[0-9a-f]{40}$ ]] || { echo 'Expected full Git SHA' >&2; exit 2; }
[[ "$mode" == unsigned || "$mode" == debug ]] || exit 2
[[ "$target" == combined || "$target" == armv7 ]] || exit 2
root=${ABYSSME_BUILD_ROOT:-"$HOME/abyssme-build"}
mkdir -p "$root"
root=$(realpath "$root")
[[ "$root" != / && "$root" != "$HOME" ]] || exit 2
exec 9>"$root/build.lock"
flock -n 9 || { echo 'An AbyssME build already holds the lock' >&2; exit 2; }
wait_for_memory() {
  for ((attempt=0; attempt<30; attempt++)); do
    local available
    available=$(awk '/MemAvailable:/ {print $2}' /proc/meminfo)
    (( available >= 4608000 )) && return 0
    echo 'Waiting for 4.5 GiB available RAM; live services take priority (maximum 10 minutes).'
    sleep 20
  done
  echo 'Insufficient host memory; retry later' >&2
  return 2
}
wait_for_memory
mkdir -p "$root/cache/gradle" "$root/cache/home" "$root/tmp" "$root/sources" "$root/out"
if [[ ! -d "$root/repo/.git" ]]; then
  git init "$root/repo"
  git -C "$root/repo" remote add origin https://github.com/EriArk/JavaEmulator.git
fi
test -z "$(git -C "$root/repo" status --porcelain --untracked-files=normal)" || {
  echo 'Remote checkout has changes; refusing to overwrite them' >&2; exit 2;
}
git -C "$root/repo" fetch --depth=1 origin "$revision"
git -C "$root/repo" -c advice.detachedHead=false checkout --detach "$revision"
test ! -e "$root/repo/keystore.properties" || { echo 'Remove signing configuration from builder' >&2; exit 2; }
if [[ "$target" == combined ]]; then
bundle="$root/sources/symbian.zip"
if [[ ! -f "$bundle" ]]; then
  curl --fail --location --retry 3 -o "$bundle.part" \
    https://github.com/EriArk/JavaEmulator/releases/download/v0.7.0-beta.1/AbyssME-0.7.0-beta.1-symbian-sources.zip
  echo "2128b3019b5e2c8adbc0c57f4fd71e576b5d2707a18e3715da49aebf258b5c55  $bundle.part" | sha256sum -c -
  mv "$bundle.part" "$bundle"
fi
echo "2128b3019b5e2c8adbc0c57f4fd71e576b5d2707a18e3715da49aebf258b5c55  $bundle" | sha256sum -c -
if [[ ! -d "$root/symbian" ]]; then
  mkdir -p "$root/sources/unpacked"
  unzip -q -o "$bundle" -d "$root/sources/unpacked"
  python3 "$root/repo/tools/restore-symbian-sources.py" "$root/sources/unpacked" "$root/symbian"
fi
test -f "$root/symbian/ABYSS_REVISION" || {
  echo 'Incomplete source restoration retained; inspect it before retrying' >&2; exit 2;
}
python3 "$root/repo/tools/restore-symbian-sources.py" "$root/sources/unpacked" "$root/symbian" --prepare-posix
fi
source_mount=()
if [[ "$target" == combined ]]; then source_mount=(-v "$root/symbian:/sources/symbian"); fi
image=abyssme-builder:android34-jdk21-v1
docker build -t "$image" "$root/repo/tools/remote-build"
run="$(date -u +%Y%m%dT%H%M%SZ)-${revision:0:12}-$mode-$target"
out="$root/out/$run"
mkdir "$out"
docker image inspect "$image" --format '{{.Id}}' > "$out/IMAGE-ID.txt"
wait_for_memory
docker run --rm --name abyssme-build-run --cpus=2 --memory=4g --memory-swap=4g \
  --pids-limit=512 --cap-drop=ALL --security-opt=no-new-privileges --read-only \
  --user "$(id -u):$(id -g)" \
  -v "$root/repo:/work" "${source_mount[@]}" \
  -v "$root/cache:/cache" -v "$root/tmp:/tmp" -v "$out:/out" \
  "$image" bash tools/remote-build/container-build.sh "$mode" "$target" 2>&1 | tee "$out/build.log"
printf 'Artifacts: %s\n' "$out"
