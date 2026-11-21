#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-only
set -eu
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to NDK r28c}"
bin="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
export LIBCLANG_PATH="${LIBCLANG_PATH:-$bin/../lib}"
export BINDGEN_EXTRA_CLANG_ARGS="--target=aarch64-linux-android29 --sysroot=$bin/../sysroot -isystem $("$bin/clang" -print-resource-dir)/include"
export CC_aarch64_linux_android="$bin/aarch64-linux-android29-clang" CXX_aarch64_linux_android="$bin/aarch64-linux-android29-clang++" AR_aarch64_linux_android="$bin/llvm-ar" CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$bin/aarch64-linux-android29-clang"
export CMAKE_TOOLCHAIN_FILE_aarch64_linux_android="$PWD/build-support/cmake/aarch64-linux-android.cmake"
cargo build --release --locked -p pocket-android-jni --no-default-features --features unicorn --target aarch64-linux-android
if [ -n "${ABYSSME_ROOT:-}" ]; then
    dest="$ABYSSME_ROOT/build/gizmondo"
    abi="$dest/jniLibs/arm64-v8a"
    notices="$dest/assets/licenses/gizmondo"
    mkdir -p "$abi" "$notices"
    cp "${CARGO_TARGET_DIR:-target}/aarch64-linux-android/release/libpockethle_jni.so" "$abi/libgizmondo_jni.so"
    cp LICENSE LICENSE-MIT LICENSE-APACHE "$notices/"
    for name in COPYING COPYING.LGPL2 COPYING_GLIB AUTHORS.TXT CREDITS.TXT; do
        cp "vendor/unicorn-engine-sys/$name" "$notices/"
    done
    mkdir -p "$notices/newtua-nsis"
    cp vendor/newtua-nsis/COPYING* vendor/newtua-nsis/POCKETHLE-CHANGES.txt "$notices/newtua-nsis/"
    cargo tree --locked -p pocket-android-jni --no-default-features --features unicorn \
        --target aarch64-linux-android -e normal --prefix none --format '{p}|{l}' > "$notices/resolved.txt"
    cp frontends/pocket-android/app/src/main/assets/licenses/xBRZ*.txt "$notices/"
fi
