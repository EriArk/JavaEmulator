#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail
engine=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$engine/../.." && pwd)
src=$(cd "${1:?Pass the pinned Handyplay checkout with its vendor dependencies}" && pwd)
out=${2:-"$root/build/mre/jniLibs/arm64-v8a"}
work=${MRE_BUILD_DIR:-"$root/build/mre/native"}
ndk=${ANDROID_NDK_HOME:-"${ANDROID_HOME:?}/ndk/28.2.13676358"}
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64"
cc="$toolchain/bin/aarch64-linux-android29-clang"
zig=${ZIG:-zig}
check_revision() {
    local actual
    actual=$(git -c safe.directory='*' -C "$1" rev-parse HEAD)
    [[ "$actual" == "$2" ]] || { echo "Unexpected source revision in $1" >&2; exit 1; }
}
check_revision "$src" 460b149302e9a52523bc314a37efabbee9f3d666
check_revision "$src/vendor/unicorn" 8028ec436f2d9376525352dd38ed9ed6b9f6be10
check_revision "$src/vendor/TinySoundFont" a3621a19c6504885cd9583e6e26c35bc954fc8ec
check_revision "$src/vendor/minimp3" 142823796ab8efa55e1bf11406028f3be77ecceb
[[ $("$zig" version) == 0.15.2 ]]
mkdir -p "$work" "$out"
# Archive the pinned core to avoid modifying an upstream checkout on repeated builds.
git -c safe.directory='*' -C "$src" archive HEAD mre-core | tar -x -C "$work"
patch --batch --fuzz=0 -d "$work" -p1 < "$engine/native/android.patch"
font=${MRE_UNIFONT:?Set MRE_UNIFONT to unifont-16.0.04.hex.gz}
echo "f9c8c7802453f47be02677176aeac2342ee96d354fad7a26cedcce48e68e1d9f  $font" | sha256sum -c -
python3 "$engine/native/pack-font.py" "$font" "$work/mre-core/core/assets/unifont.bin"
cmake -S "$src/vendor/unicorn" -B "$work/unicorn" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release -DUNICORN_ARCH=arm -DUNICORN_BUILD_TESTS=OFF -DBUILD_SHARED_LIBS=ON
cmake --build "$work/unicorn" --target unicorn --parallel "${JOBS:-2}"
cat > "$work/libc.txt" <<LIBC
include_dir=$toolchain/sysroot/usr/include
sys_include_dir=$toolchain/sysroot/usr/include/aarch64-linux-android
crt_dir=$toolchain/sysroot/usr/lib/aarch64-linux-android/29
msvc_lib_dir=
kernel32_lib_dir=
gcc_dir=
LIBC
(
    cd "$work/mre-core"
    "$zig" build-obj -O ReleaseSafe -target aarch64-linux-android -fPIC -lc \
        --libc "$work/libc.txt" --global-cache-dir "$work/zig-cache" --dep core \
        -Mroot=frontends/libretro/core.zig -I "$src/vendor/unicorn/include" \
        -Mcore=core/mreemu.zig -femit-bin="$work/mre.o"
)
"$cc" -shared -fPIC -O2 "$engine/native/frontend.c" "$work/mre.o" \
    "$src/mre-core/core/tsf_impl.c" "$src/mre-core/core/mp3_impl.c" \
    -I"$src/vendor/TinySoundFont" -I"$src/vendor/minimp3" \
    -L"$work/unicorn" -lunicorn -lm -llog -laaudio -landroid -Wl,-z,defs -o "$out/libabyssmre.so"
cp "$work/unicorn/libunicorn.so" "$out/libunicorn.so"
"$toolchain/bin/llvm-strip" --strip-unneeded "$out/libabyssmre.so" "$out/libunicorn.so"

notices="$(dirname "$(dirname "$out")")/assets/licenses/mre"
mkdir -p "$notices"
cp "$src/LICENSE" "$notices/Handyplay-AGPL-3.0.txt"
cp "$src/vendor/TinySoundFont/LICENSE" "$notices/TinySoundFont.txt"
cp "$src/vendor/minimp3/LICENSE" "$notices/minimp3.txt"
for notice in COPYING COPYING.LGPL2 COPYING_GLIB; do
    cp "$src/vendor/unicorn/$notice" "$notices/Unicorn-$notice.txt"
done
