param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$NativeBuild,
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$SourceRoot = ""
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
if (-not $SourceRoot) { $SourceRoot = Join-Path $project 'build/symbian-source-check' }
$clang = Join-Path $Sdk 'ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe'
$adb = Join-Path $Sdk 'platform-tools/adb.exe'
$output = Join-Path $project 'build/symbian-jit-memory-abort-test'
$includes = @('src/emu/cpu/include', 'src/emu/common/include', 'src/external/dynarmic/src', 'src/external/dynarmic/externals/mcl/include')
$arguments = @('--target=aarch64-linux-android29', '-static-libstdc++', '-std=c++17', '-I', (Join-Path $NativeBuild 'patched/include'))
foreach ($include in $includes) { $arguments += '-I'; $arguments += Join-Path $SourceRoot $include }
$arguments += Join-Path $PSScriptRoot 'jit_memory_abort_test.cpp'
$arguments += '-Wl,--start-group'
foreach ($library in @('src/emu/cpu/libcpu.a', 'src/emu/common/libcommon.a', 'src/external/dynarmic/src/dynarmic/libdynarmic.a', 'src/external/dynarmic/externals/mcl/src/libmcl.a', 'src/external/fmt/libfmt.a', 'src/external/spdlog/libspdlog.a', 'src/external/re2/libre2.a')) {
    $arguments += Join-Path $NativeBuild "eka2l1/$library"
}
$arguments += @('-Wl,--end-group', '-llog', '-ldl', '-landroid', '-o', $output)
& $clang @arguments
if ($LASTEXITCODE -ne 0) { throw 'JIT regression compilation failed' }
& $adb -s $Serial push $output /data/local/tmp/abyss-jit-memory-test
if ($LASTEXITCODE -ne 0) { throw 'JIT test transfer failed' }
& $adb -s $Serial shell chmod 700 /data/local/tmp/abyss-jit-memory-test
if ($LASTEXITCODE -ne 0) { throw 'JIT test setup failed' }
& $adb -s $Serial shell /data/local/tmp/abyss-jit-memory-test
if ($LASTEXITCODE -ne 0) { throw 'JIT memory abort regression failed' }
