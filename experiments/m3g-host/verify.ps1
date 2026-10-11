param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [ValidateSet('candidate', 'baseline')][string]$Engine = 'candidate',
    [string]$Adb = "$env:ANDROID_HOME/platform-tools/adb.exe"
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$package = "io.github.eriark.abyssme.m3g$Engine.debug"
$build = Join-Path $root "app/build/m3g-$Engine/outputs/apk"
$apk = Join-Path $build 'fdroid/debug/AbyssME-0.1.0-beta.2-debug.apk'
$test = Join-Path $build 'androidTest/fdroid/debug/app-fdroid-debug-androidTest.apk'
$out = Join-Path $PSScriptRoot ('build/evidence/' + ($Serial -replace '[^a-zA-Z0-9_-]', '_') + '-' + $Engine)
New-Item -ItemType Directory -Force $out | Out-Null
foreach ($artifact in @(@{path=$apk; id=$package}, @{path=$test; id="$package.test"})) {
    $metadata = Get-Content -Raw (Join-Path (Split-Path $artifact.path) 'output-metadata.json') | ConvertFrom-Json
    if ($metadata.applicationId -ne $artifact.id) { throw 'Not an isolated research application' }
}

function Invoke-Adb([string[]]$Arguments) {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $Adb
    $info.Arguments = ((@('-s', $Serial) + $Arguments) | ForEach-Object {
        if ($_ -match '"') { throw 'Unexpected quote in adb argument' }
        '"' + $_ + '"'
    }) -join ' '
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = [Diagnostics.Process]::Start($info)
    try {
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(90000)) {
            $process.Kill(); $process.WaitForExit()
            throw 'ADB timeout; no test pass can be inferred'
        }
        if ($process.ExitCode -ne 0) { throw "ADB failed: $($stderr.Result) $($stdout.Result)" }
        return $stdout.Result
    } finally { $process.Dispose() }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($apk)
try {
    $native = @($zip.Entries.FullName -match 'libjavam3g\.so$')
    $notice = $zip.Entries.FullName -contains 'assets/FREEJ2ME-LICENSE.txt'
    if ($Engine -eq 'candidate' -and ($native.Count -ne 0 -or -not $notice)) { throw 'Candidate engine isolation failed' }
    if ($Engine -eq 'baseline' -and ($native.Count -ne 2 -or $notice)) { throw 'Baseline engine isolation failed' }
    foreach ($entry in $zip.Entries | Where-Object { $_.Name -match '^classes\d*\.dex$' }) {
        $reader = [IO.StreamReader]::new($entry.Open(), [Text.Encoding]::GetEncoding(28591))
        try { $dex = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($Engine -eq 'candidate' -and $dex.Contains('Ljavax/microedition/m3g/Platform;')) { throw 'EPL bindings in candidate' }
        if ($Engine -eq 'baseline' -and $dex.Contains('Lorg/recompile/mobile/PlatformGraphics;')) { throw 'Candidate host in baseline' }
    }
} finally { $zip.Dispose() }

# Application IDs are fixed research-only IDs; never install over a user's normal build.
Invoke-Adb @('install', '-r', $apk)
Invoke-Adb @('install', '-r', $test)
$result = Invoke-Adb @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
    'javax.microedition.shell.M3gHostTest,javax.microedition.shell.M3gRasterTest', "$package.test/androidx.test.runner.AndroidJUnitRunner")
$result | Set-Content -Encoding UTF8 (Join-Path $out 'tests.txt')
[pscustomobject]@{
    engine = $Engine
    package = $package
    device = (Invoke-Adb @('shell', 'getprop', 'ro.product.model')).Trim()
    sdk = (Invoke-Adb @('shell', 'getprop', 'ro.build.version.sdk')).Trim()
    apk = (Get-FileHash $apk).Hash
    testApk = (Get-FileHash $test).Hash
} | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $out 'artifact.json')
if ($result -notmatch 'OK \(17 tests\)' -or $result -match 'FAILURES|INSTRUMENTATION_FAILED') {
    throw "Incomplete/failed host checks; see $out"
}
Invoke-Adb @('pull', "/sdcard/Android/data/$package/files/m3g-raster", $out)
Write-Output "$Engine : 17/17 host and raster checks passed; $out"
