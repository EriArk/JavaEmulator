#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Linux host. Arguments: clean checkout of the mobile adapter, ignored jniLibs output.
set -eu
source_dir=$(cd "${1:?expected pinned upstream checkout}" && pwd)
out=${2:?expected jniLibs output directory}
revision=d632a4341229f818ececeb93267dbb5554af7e65
test "$(git -C "$source_dir" rev-parse HEAD)" = "$revision"
test -z "$(git -C "$source_dir" status --porcelain -- rust)"
ndk_bin=${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ndk_bin/aarch64-linux-android29-clang"
export CC_aarch64_linux_android="$CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER"
export CXX_aarch64_linux_android="$ndk_bin/aarch64-linux-android29-clang++"
export AR_aarch64_linux_android="$ndk_bin/llvm-ar"
export CARGO_TARGET_DIR=${CARGO_TARGET_DIR:-"$source_dir/rust/target"}
cargo build --locked --manifest-path "$source_dir/rust/Cargo.toml" --target aarch64-linux-android -p wipi_android --lib
mkdir -p "$out/arm64-v8a"
"$ndk_bin/llvm-strip" --strip-unneeded -o "$out/arm64-v8a/libwipi_android.so" "$CARGO_TARGET_DIR/aarch64-linux-android/debug/libwipi_android.so"
