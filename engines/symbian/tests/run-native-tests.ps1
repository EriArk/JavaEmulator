param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$SourceRoot = ""
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$clang = Join-Path $Sdk 'ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe'
$adb = Join-Path $Sdk 'platform-tools/adb.exe'
if (-not $SourceRoot) { $SourceRoot = Join-Path $project 'build/symbian-source-check' }
foreach ($test in @('capture_cleanup', 'capture_routing', 'card_identity', 'file_transfer', 'frame_rate', 'emulation_speed')) {
    $output = Join-Path $project "build/symbian-$test-test"
    New-Item -ItemType Directory -Force (Split-Path $output) | Out-Null
    & $clang --target=aarch64-linux-android29 -static-libstdc++ -std=c++17 `
        -I (Join-Path $PSScriptRoot '../core') `
        -I (Join-Path $SourceRoot 'src/emu/common/include') `
        (Join-Path $PSScriptRoot "${test}_test.cpp") -o $output
    if ($LASTEXITCODE -ne 0) { throw 'Native test compilation failed' }
    & $adb -s $Serial push $output /data/local/tmp/abyss-symbian-capture-test
    if ($LASTEXITCODE -ne 0) { throw 'Native test transfer failed' }
    & $adb -s $Serial shell chmod 700 /data/local/tmp/abyss-symbian-capture-test
    if ($LASTEXITCODE -ne 0) { throw 'Native test setup failed' }
    & $adb -s $Serial shell /data/local/tmp/abyss-symbian-capture-test
    if ($LASTEXITCODE -ne 0) { throw 'Native capture tests failed' }
}

# Use the exact Speex implementation bundled by the pinned native engine.
$speex = Join-Path $SourceRoot 'src/external/cubeb/src'
$object = Join-Path $project 'build/audio-speex-test.o'
$output = Join-Path $project 'build/symbian-audio-speed-test'
& (Join-Path (Split-Path $clang) 'clang.exe') --target=aarch64-linux-android29 `
    -DOUTSIDE_SPEEX -DFLOATING_POINT -DEXPORT= -DRANDOM_PREFIX=speex `
    -c (Join-Path $speex 'speex/resample.c') -o $object
if ($LASTEXITCODE -ne 0) { throw 'Audio resampler test compilation failed' }
& $clang --target=aarch64-linux-android29 -static-libstdc++ -std=c++17 `
    -DOUTSIDE_SPEEX -DRANDOM_PREFIX=speex -I $speex -I (Join-Path $PSScriptRoot '../core') `
    (Join-Path $PSScriptRoot 'audio_speed_test.cpp') $object -o $output
if ($LASTEXITCODE -ne 0) { throw 'Audio speed test compilation failed' }
& $adb -s $Serial push $output /data/local/tmp/abyss-audio-speed-test
if ($LASTEXITCODE -ne 0) { throw 'Audio test transfer failed' }
& $adb -s $Serial shell chmod 700 /data/local/tmp/abyss-audio-speed-test
& $adb -s $Serial shell /data/local/tmp/abyss-audio-speed-test
if ($LASTEXITCODE -ne 0) { throw 'Audio speed tests failed' }
