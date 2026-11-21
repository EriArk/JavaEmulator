#!/bin/sh
set -eu
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME}"
bin="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
export CARGO_NDK_SYSROOT_LIBS_PATH="$bin/../sysroot/usr/lib/aarch64-linux-android"
export CCACHE_DIR="${CCACHE_DIR:-$PWD/target/ccache}"
export ANDROID_NDK="$ANDROID_NDK_HOME" ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
export ANDROID_ABI=arm64-v8a ANDROID_PLATFORM=android-29
export CXXFLAGS_aarch64_linux_android="-D_LIBCPP_ENABLE_CXX17_REMOVED_UNARY_BINARY_FUNCTION"
export CC_aarch64_linux_android="$bin/aarch64-linux-android29-clang" CXX_aarch64_linux_android="$bin/aarch64-linux-android29-clang++"
export AR_aarch64_linux_android="$bin/llvm-ar" CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$bin/aarch64-linux-android29-clang++"
export CMAKE_TOOLCHAIN_FILE_aarch64_linux_android="$PWD/abyss-toolchain.cmake"
cat > "$CMAKE_TOOLCHAIN_FILE_aarch64_linux_android" <<'EOF'
set(ANDROID_ABI arm64-v8a CACHE STRING "")
set(ANDROID_PLATFORM android-29 CACHE STRING "")
set(ANDROID_STL c++_shared CACHE STRING "")
include("$ENV{ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake")
EOF
cargo build --locked --release --target aarch64-linux-android --lib --no-default-features --features touchHLE_openal_soft_wrapper/static
