param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$clang = Join-Path $Sdk 'ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe'
$adb = Join-Path $Sdk 'platform-tools/adb.exe'
foreach ($test in @('capture_cleanup', 'capture_routing', 'card_identity', 'file_transfer', 'frame_rate')) {
    $output = Join-Path $project "build/symbian-$test-test"
    New-Item -ItemType Directory -Force (Split-Path $output) | Out-Null
    & $clang --target=aarch64-linux-android29 -static-libstdc++ -std=c++17 `
        -I (Join-Path $PSScriptRoot '../core') `
        -I (Join-Path $project 'local-games/eka2l1-audit/upstream/src/emu/common/include') `
        (Join-Path $PSScriptRoot "${test}_test.cpp") -o $output
    if ($LASTEXITCODE -ne 0) { throw 'Native test compilation failed' }
    & $adb -s $Serial push $output /data/local/tmp/abyss-symbian-capture-test
    if ($LASTEXITCODE -ne 0) { throw 'Native test transfer failed' }
    & $adb -s $Serial shell chmod 700 /data/local/tmp/abyss-symbian-capture-test
    if ($LASTEXITCODE -ne 0) { throw 'Native test setup failed' }
    & $adb -s $Serial shell /data/local/tmp/abyss-symbian-capture-test
    if ($LASTEXITCODE -ne 0) { throw 'Native capture tests failed' }
}
