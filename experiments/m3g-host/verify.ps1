param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [ValidateSet('candidate', 'baseline')][string]$Engine = 'candidate',
    [ValidateSet('fdroid', 'phone')][string]$Variant = 'fdroid',
    [string]$ApkRoot,
    [switch]$ReleaseCheck,
    [switch]$Production,
    [ValidateRange(30,3600)][int]$TestTimeoutSeconds = 300,
    [string]$Adb = "$env:ANDROID_HOME/platform-tools/adb.exe"
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if ($Production -and ($ReleaseCheck -or $Engine -ne 'candidate')) { throw 'Production verification is for the normal candidate debug build only' }
$package = "io.github.eriark.abyssme.m3g$Engine.debug"
if ($Variant -eq 'phone') { $package = "io.github.eriark.abyssme.m3g$Engine.phone.debug" }
$kind = if ($ReleaseCheck) { 'release' } else { 'debug' }
$buildSuffix = if ($ReleaseCheck) { '-release-check' } else { '' }
if ($ReleaseCheck) {
    $package = 'io.github.eriark.abyssme.m3grehearsal'
    if ($Variant -eq 'phone') { $package += '.phone' }
}
if ($Production) {
    $package = 'io.github.eriark.abyssme'
    if ($Variant -eq 'phone') { $package += '.phone' }
    $package += '.debug'
}
$build = if ($ApkRoot) { (Resolve-Path $ApkRoot).Path } else { Join-Path $root "app/build/m3g-$Engine$buildSuffix/outputs/apk" }
if ($Production -and !$ApkRoot) { $build = Join-Path $root 'app/build/outputs/apk' }
$suffix = if ($Variant -eq 'phone') { '-phone' } else { '' }
$appMetadata = Get-Content -Raw (Join-Path $build "$Variant/$kind/output-metadata.json") | ConvertFrom-Json
if ($appMetadata.elements.Count -ne 1 -or [IO.Path]::GetFileName($appMetadata.elements[0].outputFile) -ne $appMetadata.elements[0].outputFile) { throw 'Expected one APK filename in metadata' }
$apk = Join-Path $build "$Variant/$kind/$($appMetadata.elements[0].outputFile)"
$test = Join-Path $build "androidTest/$Variant/$kind/app-$Variant-$kind-androidTest.apk"
$out = Join-Path $PSScriptRoot ('build/evidence/' + ($Serial -replace '[^a-zA-Z0-9_-]', '_') + '-' + $Engine)
if ($Variant -eq 'phone') { $out += '-phone' }
if ($ReleaseCheck) { $out += '-release-check' }
if ($Production) { $out += '-production' }
New-Item -ItemType Directory -Force $out | Out-Null
foreach ($artifact in @(@{path=$apk; id=$package}, @{path=$test; id="$package.test"})) {
    $metadata = Get-Content -Raw (Join-Path (Split-Path $artifact.path) 'output-metadata.json') | ConvertFrom-Json
    if ($metadata.applicationId -ne $artifact.id) { throw 'Unexpected application identity' }
}

function Invoke-Adb([string[]]$Arguments, [int]$TimeoutMs = 90000) {
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
        if (-not $process.WaitForExit($TimeoutMs)) {
            $process.Kill(); $process.WaitForExit()
            if ($Arguments.Length -ge 3 -and ($Arguments[0..2] -join ' ') -eq 'shell am instrument') {
                $stdout.Result | Set-Content -Encoding UTF8 (Join-Path $out 'tests-timeout.txt')
                # Killing the local adb client alone can leave the GPU suite running.
                try { Invoke-Adb @('shell', 'am', 'force-stop', $package) -TimeoutMs 5000 | Out-Null }
                catch { Write-Warning "Could not stop the isolated test package: $_" }
            }
            throw 'ADB timeout; no test pass can be inferred'
        }
        if ($process.ExitCode -ne 0) { throw "ADB failed: $($stderr.Result) $($stdout.Result)" }
        return $stdout.Result
    } finally { $process.Dispose() }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$measured = $false
$zip = [IO.Compression.ZipFile]::OpenRead($apk)
try {
    $native = @($zip.Entries.FullName -match 'libjavam3g\.so$')
    $notice = $zip.Entries.FullName -contains 'assets/FREEJ2ME-LICENSE.txt'
    if ($Engine -eq 'candidate' -and ($native.Count -ne 0 -or -not $notice)) { throw 'Candidate engine isolation failed' }
    if ($Engine -eq 'candidate') {
        $provenance = $zip.GetEntry('assets/FREEJ2ME-SOURCE.json')
        if ($null -eq $provenance) { throw 'Missing pinned-source provenance' }
        $reader = [IO.StreamReader]::new($provenance.Open())
        try { $source = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        $lock = Get-Content -Raw (Join-Path $root 'experiments/m3g-probe/source-lock.json') | ConvertFrom-Json
        if ($source.revision -ne $lock.revision -or $source.sha256 -ne $lock.sha256 -or $source.archive -ne $lock.archive) {
            throw 'Candidate source identity mismatch'
        }
    }
    if ($Engine -eq 'baseline' -and ($native.Count -ne 2 -or $notice)) { throw 'Baseline engine isolation failed' }
    foreach ($entry in $zip.Entries | Where-Object { $_.Name -match '^classes\d*\.dex$' }) {
        $reader = [IO.StreamReader]::new($entry.Open(), [Text.Encoding]::GetEncoding(28591))
        try { $dex = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($dex.Contains('Lorg/abyssme/research/FrameMeter;')) { $measured = $true }
        if ($Engine -eq 'candidate' -and $dex.Contains('Ljavax/microedition/m3g/Platform;')) { throw 'EPL bindings in candidate' }
        if ($Engine -eq 'candidate') {
            foreach ($legacy in @('BlockEndEvent', 'BlockStartEvent', 'Event', 'EventList',
                    'MidiSequence', 'MidiSequenceException', 'PlayBlockEvent', 'RepeatEvent',
                    'ToneEvent', 'VolumeEvent')) {
                if ($dex.Contains("Ljavax/microedition/media/tone/$legacy;")) { throw "EPL tone type in candidate: $legacy" }
            }
        }
        if ($Engine -eq 'baseline' -and $dex.Contains('Lorg/recompile/mobile/PlatformGraphics;')) { throw 'Candidate host in baseline' }
    }
} finally { $zip.Dispose() }

# Fixed research IDs or an explicitly requested normal debug ID, never a release ID.
Invoke-Adb @('install', '-r', $apk)
Invoke-Adb @('install', '-r', $test)
$classes = 'javax.microedition.shell.M3gHostTest,javax.microedition.shell.M3gRasterTest,javax.microedition.shell.M3gSceneTest'
$classes += ',javax.microedition.shell.M3gCopyTest'
$expectedTests = 62
if ($Engine -eq 'candidate') {
    # The unchanged native baseline has known spec deviations in this comparison suite.
    $classes += ',javax.microedition.shell.M3gAnimationTest'
    $expectedTests += 23
    $classes += ',javax.microedition.shell.M3gDeformationTest'
    $expectedTests += 21
    $classes += ',javax.microedition.shell.M3gSceneContractTest,javax.microedition.shell.M3gLoaderContractTest'
    $classes += ',javax.microedition.m3g.GpuSceneContractTest,javax.microedition.m3g.FragmentAlphaContractTest'
    $expectedTests += 76
    $classes += ',javax.microedition.shell.TonePlaybackTest,javax.microedition.m3g.M3gMathTest,javax.microedition.m3g.NativeRasterizerTest'
    $classes += ',javax.microedition.m3g.MipmapSamplingTest'
    $classes += ',javax.microedition.m3g.GeometryPreparationTest'
    $classes += ',javax.microedition.m3g.GpuRasterizerTest'
    $classes += ',javax.microedition.m3g.GpuTextureTest'
    $classes += ',javax.microedition.m3g.GpuEffectsTest'
    $classes += ',javax.microedition.m3g.GpuSpriteTest'
    $classes += ',javax.microedition.m3g.GpuRecoveryTest'
    $classes += ',javax.microedition.m3g.GpuBatchTest'
    $classes += ',javax.microedition.m3g.GpuCacheTest'
    $classes += ',javax.microedition.m3g.GpuGeometryTest'
    $classes += ',javax.microedition.m3g.GpuHardwareSceneContractTest'
    $classes += ',javax.microedition.m3g.GpuReleaseTest'
    $classes += ',javax.microedition.m3g.GpuMeshCacheTest'
    $classes += ',javax.microedition.m3g.GpuLightingTest'
    $classes += ',javax.microedition.m3g.GpuThroughputTest'
    $classes += ',javax.microedition.m3g.GpuTransferTest'
    $expectedTests += 181
}
if ($measured) {
    $classes += ',javax.microedition.shell.M3gTargetMeterTest'
    $expectedTests += 8
}
if ($ReleaseCheck -and $Engine -eq 'candidate') {
    # These white-box suites access diagnostic switches/counters deliberately
    # removed by R8. Keep them in debug checks, not in the optimized APK.
    $classes = (($classes -split ',') | Where-Object {
        $_ -notin @('javax.microedition.m3g.GpuThroughputTest', 'javax.microedition.m3g.NativeRasterizerTest')
    }) -join ','
    $expectedTests -= 39
}
$result = Invoke-Adb @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
    $classes, "$package.test/androidx.test.runner.AndroidJUnitRunner") -TimeoutMs ($TestTimeoutSeconds * 1000)
$result | Set-Content -Encoding UTF8 (Join-Path $out 'tests.txt')
[pscustomobject]@{
    engine = $Engine
    variant = $Variant
    releaseCheck = [bool]$ReleaseCheck
    production = [bool]$Production
    package = $package
    device = (Invoke-Adb @('shell', 'getprop', 'ro.product.model')).Trim()
    sdk = (Invoke-Adb @('shell', 'getprop', 'ro.build.version.sdk')).Trim()
    apk = (Get-FileHash $apk).Hash
    testApk = (Get-FileHash $test).Hash
    measured = $measured
    skipped = ([regex]::Matches($result, 'INSTRUMENTATION_STATUS_CODE: -4')).Count
} | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $out 'artifact.json')
if ($result -notmatch "OK \($expectedTests tests\)" -or $result -match 'FAILURES|INSTRUMENTATION_FAILED') {
    throw "Incomplete/failed host checks; see $out"
}
Invoke-Adb @('pull', "/sdcard/Android/data/$package/files/m3g-raster", $out)
$skipped = ([regex]::Matches($result, 'INSTRUMENTATION_STATUS_CODE: -4')).Count
Write-Output "$Engine : $($expectedTests - $skipped) passed, $skipped skipped, $expectedTests total; $out"
